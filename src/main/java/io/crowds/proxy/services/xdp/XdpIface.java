package io.crowds.proxy.services.xdp;

import io.crowds.compoments.xdp.*;
import io.crowds.lib.unix.Unix;
import io.crowds.lib.xdp.*;
import io.crowds.lib.xdp.ffi.BpfMap;
import io.crowds.lib.xdp.ffi.LibBpf;
import io.crowds.util.IPMask;
import io.crowds.util.Inet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.buffer.ByteArray;
import org.stormpx.net.network.Iface;
import org.stormpx.net.network.IfaceIngress;
import org.stormpx.net.network.NetworkParams;
import org.stormpx.net.pkt.PktBuf;
import org.stormpx.net.util.Checksum;
import org.stormpx.net.util.IP;
import org.stormpx.net.util.Mac;
import org.stormpx.net.util.PacketUtils;
import top.dreamlike.panama.generator.proxy.MemoryLifetimeScope;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Gatherers;

public class XdpIface implements Iface, XdpIngressHandler {
    private static final Logger logger = LoggerFactory.getLogger(XdpIface.class);
    private final static String XDP_PROG = "xdp_redirect_prog.o";
    private final static String XSK_MAP_NAME = "xsks_map";
    private final static String MAC_MSG_RING = "mac_message_ring";
    private final static String BYPASS_IPV4_MAP = "bypass_ipv4_lpm";
    private final static String BYPASS_IPV6_MAP = "bypass_ipv6_lpm";
    private final String iface;
    private final int ifindex;
    private final XdpOpt opt;

    private Mac localMac;
    private IP localIp;

    private XdpProg prog;
    private Umem umem;
    private List<XdpSocket> sockets;
    private BpfRingBuffer macRingBuffer;

    private List<XdpPoller> pollers;
    private AtomicLong pollerSeq = new AtomicLong(0);
    private IfaceIngress ifaceIngress;

    public XdpIface(String iface, XdpOpt opt) {
        this.iface = iface;
        this.opt = opt;
        try {
            this.ifindex = MemoryLifetimeScope.local().active(()-> Unix.INSTANCE.if_nametoindex(iface));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    private boolean handleMacMessage(MacMessage macMessage){
        try {
            MemorySegment sourceMac = macMessage.getSource();
            MemorySegment destMac = macMessage.getDest();
            MemorySegment mac = macMessage.getMac();
            ByteArray pkt;
            if (!macMessage.isIpv6()){
                MacMessage.Ipv4 ipv4 = macMessage.getIp().getIpv4();
                ByteArray arpPkt = ByteArray.alloc(14 + 28);
                arpPkt.setBuffer(0,ByteArray.wrap(sourceMac.asByteBuffer()),0,6);
                arpPkt.setBuffer(6,ByteArray.wrap(destMac.asByteBuffer()),0,6);
                arpPkt.setShort(12, (short) 0x0806);
                arpPkt.setShort(14, (short) 1);
                arpPkt.setShort(16, (short) 0x0800);
                arpPkt.set(18, (byte) 6);
                arpPkt.set(19, (byte) 4);
                arpPkt.setShort(20, (short) 2);
                arpPkt.setBuffer(22,ByteArray.wrap(mac.asByteBuffer()),0,6);
                arpPkt.setBuffer(28,ByteArray.wrap(ipv4.getAddr().asByteBuffer()),0,4);
                arpPkt.setBuffer(32,ByteArray.wrap(localMac.getBytes()),0,6);
                arpPkt.setBuffer(38,ByteArray.wrap(localIp.getBytes()),0,4);
                pkt = arpPkt;
            }else{
                MacMessage.Ipv6 ipv6 = macMessage.getIp().getIpv6();
                var sourceIp = IP.of(ipv6.getSource().toArray(ValueLayout.JAVA_BYTE));
                var destIp = IP.of(ipv6.getDest().toArray(ValueLayout.JAVA_BYTE));
                //ethhdr + ipv6hdr + icmphdr + target_address + option
                ByteArray nrAdvPkt = ByteArray.alloc(14 + 40 + 8 + 16 + 8);
                nrAdvPkt.setBuffer(0,ByteArray.wrap(sourceMac.asByteBuffer()),0,6);
                nrAdvPkt.setBuffer(6,ByteArray.wrap(destMac.asByteBuffer()),0,6);
                nrAdvPkt.setShort(12, (short) 0x86dd);
                nrAdvPkt.setInt(14,0x60000000);
                //ipv6hdr
                nrAdvPkt.setShort(18, (short) 26);
                nrAdvPkt.set(20, (byte) 58);
                nrAdvPkt.set(21, (byte) 64);
                nrAdvPkt.setBuffer(22,ByteArray.wrap(sourceIp.getBytes()),0,16);
                nrAdvPkt.setBuffer(38,ByteArray.wrap(destIp.getBytes()),0,16);
                //icmphdr
                nrAdvPkt.set(54, (byte) 136);
                nrAdvPkt.set(55, (byte) 0);
                nrAdvPkt.setShort(56, (short) 0);
                //nr_adv
                nrAdvPkt.setInt(58,((int)ipv6.getFlags())<<24);
                nrAdvPkt.setBuffer(62,ByteArray.wrap(ipv6.getAddr().asByteBuffer()),0,16);
                //nr_adv option
                nrAdvPkt.set(78, (byte) 2);
                nrAdvPkt.set(79, (byte) 1);
                nrAdvPkt.setBuffer(80,ByteArray.wrap(mac.asByteBuffer()),0,6);
                ByteArray icmpSlice = nrAdvPkt.getSlice(54);
                int checksum = PacketUtils.calculateChecksumWithPseudoHeader(58,sourceIp,destIp, icmpSlice,icmpSlice.length());
                if (checksum==0){
                    checksum = 0xffff;
                }
                nrAdvPkt.setShort(56, (short) checksum);

                pkt = nrAdvPkt;
            }
            if (ifaceIngress.enqueue(pkt)) {
                ifaceIngress.callback();
            }

        } catch (Exception e) {
            logger.error("handle mac message failed",e);
        }
        return true;
    }

    private BpfRingBuffer newMacRingBuffer(XdpProg xdpProg){
        int ringMapFd = xdpProg.mapFd(MAC_MSG_RING);
        if (ringMapFd<=0) {
            return null;
        }
        return Xdp.newRingBuffer(ringMapFd, MacMessage.class,this::handleMacMessage);
    }



    private void configureBypassMap(XdpProg xdpProg){
        List<String> ips = opt.getBypassIps();
        if (ips==null||ips.isEmpty()){
            return;
        }
        BpfMap ipv4Lpm = xdpProg.map(BYPASS_IPV4_MAP);
        BpfMap ipv6Lpm = xdpProg.map(BYPASS_IPV6_MAP);
        assert ipv4Lpm!=null;
        assert ipv6Lpm!=null;

        try (Arena arena = Arena.ofConfined()){
            for (String s : ips) {
                try {
                    IPMask ipMask = Inet.parseIPMask(s);
                    var address = ipMask.ip().getBytes();
                    var mask = ipMask.mask();
                    var key = arena.allocate(address.length + 4);
                    var value = arena.allocate(ValueLayout.JAVA_INT);
                    key.set(ValueLayout.JAVA_INT,0,mask);
                    MemorySegment.copy(MemorySegment.ofArray(address),0,key,4,address.length);
                    value.set(ValueLayout.JAVA_INT,0,1);
                    BpfMap targetMap = address.length==4? ipv4Lpm : ipv6Lpm;
                    int ret = LibBpf.INSTANCE.bpf_map__update_elem(targetMap,key, key.byteSize(), value,value.byteSize(), 0);
                    if (ret != 0) {
                        MemorySegment errMsg = arena.allocate(512);
                        LibBpf.INSTANCE.libbpf_strerror(ret, errMsg);
                        throw new RuntimeException(errMsg.getString(0));
                    }
                } catch (Exception e) {
                    logger.error("Add {} to bypass_map failed: {}",s,e.getMessage());
                }
            }
        }
    }

    private void configureXskMap(XdpProg xdpProg, List<XdpSocket> xsks){
        BpfMap xsksMap = xdpProg.map(XSK_MAP_NAME);
        if (xsksMap==null){
            throw new RuntimeException("map "+XSK_MAP_NAME+" not found");
        }
        try (Arena arena = Arena.ofConfined()){
            var key = arena.allocate(ValueLayout.JAVA_INT);
            var value = arena.allocate(ValueLayout.JAVA_INT);
            for (int i = 0; i < xsks.size(); i++) {
                var xsk = xsks.get(i);
                key.set(ValueLayout.JAVA_INT,0,i);
                value.set(ValueLayout.JAVA_INT,0, xsk.fd());
                int ret = LibBpf.INSTANCE.bpf_map__update_elem(xsksMap,key, key.byteSize(), value,value.byteSize(), 0);
                if (ret != 0) {
                    MemorySegment errMsg = arena.allocate(512);
                    LibBpf.INSTANCE.libbpf_strerror(ret, errMsg);
                    throw new RuntimeException("Update xsk map failed: " + errMsg.getString(0));
                }
            }
        }
    }

    @Override
    public void init(NetworkParams networkParams, IfaceIngress ifaceIngress) {
        this.localIp = networkParams.localIps().getFirst();
        this.localMac = networkParams.macAddress();
        this.ifaceIngress = ifaceIngress;

        if (opt.isUnload()){
            Xdp.unloadXdpProg(ifindex);
        }

        int umemSize = opt.getUmemSize();
        int frameSize = opt.getFrameSize();
        Umem umem = Xdp.createUmem(umemSize / frameSize, frameSize, opt.getFillSize(), opt.getCompSize());
        int queue = Math.max(1,opt.getQueue());
        List<XdpSocket> sockets = new ArrayList<>();
        for (int i = 0; i < queue; i++) {
            sockets.add(Xdp.createXsk(iface,i,umem, opt.getRxSize(), opt.getTxSize()));
        }
        UmemBufferPoll umemBufferPoll = new UmemBufferPoll(umem, 24);

        List<XdpPoller> pollers = new ArrayList<>();
        sockets.stream()
               .gather(Gatherers.windowFixed(Math.max(1,sockets.size()/opt.getThreads())))
               .forEach(skts->{
                   pollers.add(new XdpPoller(umemBufferPoll, skts, false,this));
               });

        XdpProg prog = Xdp.findFile(XDP_PROG, null, ifindex);
        prog.attach(opt.getMode());
        BpfRingBuffer macRingBuffer = newMacRingBuffer(prog);
        if (macRingBuffer!=null) {
            pollers.getFirst().addRingBuffer(macRingBuffer);
        }
        configureBypassMap(prog);
        configureXskMap(prog,sockets);



        logger.info("init XDP iface with opt: {}", opt);

        for (XdpPoller poller : pollers) {
            poller.start().join();
        }

        this.umem = umem;
        this.sockets = sockets;
        this.macRingBuffer = macRingBuffer;
        this.pollers = pollers;
        this.prog = prog;

    }


    @Override
    public long flags() {
        return Iface.IF_TX_CONCURRENT | (opt.isTxChecksum()?Iface.PKT_TX_L4_CSUM:0);
    }

    @Override
    public boolean transmit(ByteArray data) {
        return true;
    }

    @Override
    public boolean transmit(PktBuf pktBuf) {
        int csumStart = -1;
        int csumOffset = 0;
        if (pktBuf.l4Checksum() instanceof Checksum.Offload){
            int offset = switch (pktBuf.ipProtocol()){
                //icmp
                case 1 -> 2;
                //tcp
                case 6 -> 16;
                //udp
                case 17 -> 6;
                default -> -1;
            };
            if (offset!=-1) {
                csumStart = pktBuf.l2Len() + pktBuf.l3Len();
                csumOffset = offset;
            }
        }
        TxDesc txDesc = new TxDesc(csumStart, csumOffset,false, pktBuf.buffer());
        pollers.get((int) (pollerSeq.getAndIncrement() % pollers.size())).transmitData(txDesc);
        return true;
    }

    @Override
    public void destroy() {
        for (XdpPoller poller : this.pollers) {
            poller.stop().join();
        }
        for (XdpSocket socket : this.sockets) {
            socket.delete();
        }
        this.umem.delete();
        if (this.macRingBuffer!=null){
            this.macRingBuffer.free();
        }
        this.prog.detach();

    }


    @Override
    public void handle(RxDesc rxDesc) {
        MemorySegment data = rxDesc.data().asSlice(rxDesc.offset(), rxDesc.len());
        ByteArray buffer = ByteArray.alloc((int) data.byteSize());
        buffer.setBuffer(0,ByteArray.wrap(data.asByteBuffer()),0,buffer.length());
        if (!ifaceIngress.enqueue(buffer)){
            ifaceIngress.callback();
        }
    }

    @Override
    public void complete() {
        ifaceIngress.callback();
    }
}
