/* SPDX-License-Identifier: GPL-2.0 */

#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>
#include <xdp/xdp_helpers.h>
#include <xdp/parsing_helpers.h>

#define DEFAULT_QUEUE_IDS 128

/* ICMPv6 Neighbor Advertisement (RFC 4861), absent from <linux/icmpv6.h> */
#ifndef ND_NEIGHBOR_ADVERT
#define ND_NEIGHBOR_ADVERT 136
#endif

struct {
	__uint(type, BPF_MAP_TYPE_XSKMAP);
	__uint(key_size, sizeof(int));
	__uint(value_size, sizeof(int));
	__uint(max_entries, DEFAULT_QUEUE_IDS);
} xsks_map SEC(".maps");

struct {
	__uint(priority, 20);
	__uint(XDP_PASS, 1);
} XDP_RUN_CONFIG(xdp_redirect_prog);

struct mac_message{
	unsigned char source[ETH_ALEN];
	unsigned char dest[ETH_ALEN];
	unsigned char mac[ETH_ALEN];
	__u32 type; //4 for ipv4, 6 for ipv6
	union{
		struct {
			__be32 addr;
		} ipv4;
		struct {
			struct in6_addr source;
			struct in6_addr dest;
			__be32 flags;
			struct in6_addr addr;
		} ipv6 ;
	};
};

struct {
	__uint(type, BPF_MAP_TYPE_RINGBUF);
	__uint(max_entries, 32 * 1024 /* 32 KB */);
} mac_message_ring SEC(".maps");

struct ipv4_lpm_key {
        __u32 prefixlen;
        __u32 data;
};

struct {
        __uint(type, BPF_MAP_TYPE_LPM_TRIE);
        __type(key, struct ipv4_lpm_key);
        __type(value, __u32);
        __uint(map_flags, BPF_F_NO_PREALLOC);
        __uint(max_entries, 255);
} bypass_ipv4_lpm SEC(".maps");


struct ipv6_lpm_key {
        __u32 prefixlen;
		__u8 data[16];
};

struct {
        __uint(type, BPF_MAP_TYPE_LPM_TRIE);
        __type(key, struct ipv6_lpm_key);
        __type(value, __u32);
        __uint(map_flags, BPF_F_NO_PREALLOC);
        __uint(max_entries, 255);
} bypass_ipv6_lpm SEC(".maps");

/*
 * pkt_ctx: 解析报文过程中逐层填充的"必要信息",
 * 之后根据它组装各调用所需的入参(mac_message / bpf_sock_tuple / LPM key)。
 */
struct pkt_ctx {
	unsigned char src_mac[ETH_ALEN];	/* 以太网源 MAC */
	unsigned char dst_mac[ETH_ALEN];	/* 以太网目的 MAC */
	unsigned char neigh_mac[ETH_ALEN];	/* 学习到的邻居 MAC(ARP sender / NA 源) */
	__u16 eth_type;				/* 以太网载荷类型(主机序) */
	__u8 family;				/* 4: IPv4, 6: IPv6 */
	__u8 ip_proto;				/* 传输层协议 IPPROTO_* */
	__be16 sport;				/* L4 源端口 */
	__be16 dport;				/* L4 目的端口 */
	union {
		__be32 v4;
		struct in6_addr v6;
	} src_ip;				/* L3 源地址 */
	union {
		__be32 v4;
		struct in6_addr v6;
	} dst_ip;				/* L3 目的地址 */
	union {
		__be32 v4;
		struct in6_addr v6;
	} neigh_ip;				/* 学习到的邻居 IP(ARP sender / NA target) */
	__be32 na_flags;			/* ICMPv6 NA 标志(仅 NA 使用) */
};

/* parse_packet() 的返回动作 */
enum {
	PKT_PASS = 0,		/* 无需特殊处理, 透传内核协议栈 */
	PKT_LEARN,		/* ARP 响应 / NA: 学习邻居 */
	PKT_REDIRECT,		/* TCP/UDP: 查 socket 与 bypass */
};

/* 解析 TCP/UDP 端口填入 ctx; 非 TCP/UDP 或解析失败返回 -1 */
static __always_inline int parse_l4_ports(struct hdr_cursor *nh,
					  void *data_end, __u8 proto,
					  struct pkt_ctx *pkt)
{
	struct tcphdr *tcph;
	struct udphdr *udph;

	if (proto == IPPROTO_TCP) {
		if (parse_tcphdr(nh, data_end, &tcph) < 0)
			return -1;
		pkt->sport = tcph->source;
		pkt->dport = tcph->dest;
	} else if (proto == IPPROTO_UDP) {
		if (parse_udphdr(nh, data_end, &udph) < 0)
			return -1;
		pkt->sport = udph->source;
		pkt->dport = udph->dest;
	} else {
		return -1;
	}
	return 0;
}

/*
 * 解析整个报文并填充 pkt_ctx(只做"记录",不做任何决策);
 * 返回后续需要执行的动作 PKT_*。
 */
static __always_inline int parse_packet(struct xdp_md *ctx,
					struct pkt_ctx *pkt)
{
	void *data_end = (void *)(long)ctx->data_end;
	struct hdr_cursor nh;
	struct ethhdr *eth;
	int eth_type;

	nh.pos = (void *)(long)ctx->data;

	/* L2: 以太网头(自动跳过 VLAN) */
	eth_type = parse_ethhdr(&nh, data_end, &eth);
	if (eth_type < 0)
		return PKT_PASS;
	__builtin_memcpy(pkt->src_mac, eth->h_source, ETH_ALEN);
	__builtin_memcpy(pkt->dst_mac, eth->h_dest, ETH_ALEN);
	pkt->eth_type = bpf_ntohs(eth_type);

	/* ARP 响应: 学习 sender IP -> sender MAC */
	if (eth_type == bpf_htons(ETH_P_ARP)) {
		struct arphdr *arp;

		if (parse_arphdr(&nh, data_end, &arp) != bpf_htons(ARPOP_REPLY))
			return PKT_PASS;
		pkt->family = 4;
		__builtin_memcpy(pkt->neigh_mac, arp->ar_sha, ETH_ALEN);
		pkt->neigh_ip.v4 = arp->ar_sip;
		return PKT_LEARN;
	}

	/* IPv4 */
	if (eth_type == bpf_htons(ETH_P_IP)) {
		struct iphdr *iph;
		int proto;

		if (parse_iphdr(&nh, data_end, &iph) < 0)
			return PKT_PASS;
		pkt->family = 4;
		pkt->src_ip.v4 = iph->saddr;
		pkt->dst_ip.v4 = iph->daddr;
		proto = iph->protocol;
		pkt->ip_proto = proto;
		if (parse_l4_ports(&nh, data_end, proto, pkt) < 0)
			return PKT_PASS;
		return PKT_REDIRECT;
	}

	/* IPv6 */
	if (eth_type == bpf_htons(ETH_P_IPV6)) {
		struct ipv6hdr *ip6h;
		int proto;

		/* 自动跳过 IPv6 扩展头, 返回 L4 协议 */
		proto = parse_ip6hdr(&nh, data_end, &ip6h);
		if (proto < 0)
			return PKT_PASS;
		pkt->family = 6;
		pkt->src_ip.v6 = ip6h->saddr;
		pkt->dst_ip.v6 = ip6h->daddr;
		pkt->ip_proto = proto;

		/* ICMPv6: 仅 Neighbor Advertisement 学习 target IP -> 源 MAC */
		if (proto == IPPROTO_ICMPV6) {
			struct icmp6hdr *icmp6h;
			struct in6_addr *target;

			if (parse_icmp6hdr(&nh, data_end, &icmp6h) < 0 ||
			    icmp6h->icmp6_type != ND_NEIGHBOR_ADVERT)
				return PKT_PASS;

			/* NA 的 target 地址紧随 ICMPv6 头之后 */
			target = nh.pos;
			if ((void *)(target + 1) > data_end)
				return PKT_PASS;
			pkt->na_flags = icmp6h->icmp6_dataun.un_data32[0];
			pkt->neigh_ip.v6 = *target;
			__builtin_memcpy(pkt->neigh_mac, eth->h_source, ETH_ALEN);
			return PKT_LEARN;
		}

		/* 仅 TCP/UDP 需要继续, 其余透传 */
		if (parse_l4_ports(&nh, data_end, proto, pkt) < 0)
			return PKT_PASS;
		return PKT_REDIRECT;
	}

	return PKT_PASS;
}

/* 从 pkt_ctx 组装 mac_message(ARP 响应 / NA 两种邻居学习共用) */
static __always_inline void build_mac_message(const struct pkt_ctx *pkt,
					      struct mac_message *msg)
{
	*msg = (struct mac_message){};

	__builtin_memcpy(msg->source, pkt->src_mac, ETH_ALEN);
	__builtin_memcpy(msg->dest, pkt->dst_mac, ETH_ALEN);
	__builtin_memcpy(msg->mac, pkt->neigh_mac, ETH_ALEN);
	msg->type = pkt->family;	/* 4 for ipv4, 6 for ipv6 */

	if (pkt->family == 4) {
		msg->ipv4.addr = pkt->neigh_ip.v4;
	} else {
		msg->ipv6.source = pkt->src_ip.v6;
		msg->ipv6.dest = pkt->dst_ip.v6;
		msg->ipv6.addr = pkt->neigh_ip.v6;
		msg->ipv6.flags = pkt->na_flags;
	}
}

/* 将组装好的 mac_message 写入 ringbuf 上报用户态 */
static __always_inline int report_mac_message(const struct mac_message *msg)
{
	struct mac_message *slot;

	slot = bpf_ringbuf_reserve(&mac_message_ring, sizeof(*slot), 0);
	if (!slot)
		return XDP_PASS;
	*slot = *msg;
	bpf_ringbuf_submit(slot, 0);
	return XDP_PASS;
}

/* 从 pkt_ctx 组装 bpf_sock_tuple(5 元组) */
static __always_inline void build_tuple(const struct pkt_ctx *pkt,
					struct bpf_sock_tuple *tuple)
{
	if (pkt->family == 4) {
		tuple->ipv4.saddr = pkt->src_ip.v4;
		tuple->ipv4.daddr = pkt->dst_ip.v4;
		tuple->ipv4.sport = pkt->sport;
		tuple->ipv4.dport = pkt->dport;
	} else {
		__builtin_memcpy(tuple->ipv6.saddr, &pkt->src_ip.v6,
				 sizeof(tuple->ipv6.saddr));
		__builtin_memcpy(tuple->ipv6.daddr, &pkt->dst_ip.v6,
				 sizeof(tuple->ipv6.daddr));
		tuple->ipv6.sport = pkt->sport;
		tuple->ipv6.dport = pkt->dport;
	}
}

/* 按协议选 bpf_sk_lookup_tcp/udp 查内核 socket */
static __always_inline struct bpf_sock *
lookup_socket(struct xdp_md *ctx, struct bpf_sock_tuple *tuple,
	      __u32 tuple_size, __u8 proto)
{
	if (proto == IPPROTO_TCP)
		return bpf_sk_lookup_tcp(ctx, tuple, tuple_size,
					 BPF_F_CURRENT_NETNS, 0);
	return bpf_sk_lookup_udp(ctx, tuple, tuple_size,
				 BPF_F_CURRENT_NETNS, 0);
}

/* TCP/UDP 处理: 内核 socket -> bypass 前缀 -> 重定向 AF_XDP */
static __always_inline int redirect_tcp_udp(struct xdp_md *ctx,
					    const struct pkt_ctx *pkt,
					    int index)
{
	struct bpf_sock_tuple tuple;
	struct bpf_sock *sk;
	__u32 *bypass;

	build_tuple(pkt, &tuple);

	/* 命中内核 socket: 释放引用并透传内核协议栈 */
	if (pkt->family == 4)
		sk = lookup_socket(ctx, &tuple, sizeof(tuple.ipv4), pkt->ip_proto);
	else
		sk = lookup_socket(ctx, &tuple, sizeof(tuple.ipv6), pkt->ip_proto);
	if (sk) {
		bpf_sk_release(sk);
		return XDP_PASS;
	}

	/* 目的地址命中 bypass 前缀列表: 透传 */
	if (pkt->family == 4) {
		struct ipv4_lpm_key key = { .prefixlen = 32,
					    .data = pkt->dst_ip.v4 };

		bypass = bpf_map_lookup_elem(&bypass_ipv4_lpm, &key);
	} else {
		struct ipv6_lpm_key key = { .prefixlen = 128 };

		__builtin_memcpy(key.data, &pkt->dst_ip.v6, sizeof(key.data));
		bypass = bpf_map_lookup_elem(&bypass_ipv6_lpm, &key);
	}
	if (bypass && *bypass)
		return XDP_PASS;

	/* 其余重定向到对应 queue 的 AF_XDP socket */
	return bpf_redirect_map(&xsks_map, index, XDP_PASS);
}


SEC("xdp")
int xdp_redirect_prog(struct xdp_md *ctx)
{
	struct pkt_ctx pkt = {};
	int action;
	int index = ctx->rx_queue_index;

	/* 1. 解析并分类: 解析结果全部记录在 pkt_ctx 中 */
	action = parse_packet(ctx, &pkt);

	/* 2. ARP 响应 / NA: 由 ctx 组装 mac_message 上报, 然后透传 */
	if (action == PKT_LEARN) {
		struct mac_message msg;

		build_mac_message(&pkt, &msg);
		return report_mac_message(&msg);
	}

	/* 3. TCP/UDP: 由 ctx 组装 tuple / LPM key 判断重定向 */
	if (action == PKT_REDIRECT)
		return redirect_tcp_udp(ctx, &pkt, index);

	/* 4. 其它: 透传内核协议栈 */
	return XDP_PASS;
}

char _license[] SEC("license") = "GPL";
