# ddnsp

DDNS + DNS + Proxy 一体化网络工具，基于 Java 和 Netty 构建。

## 功能特性

### DNS 服务器
- 支持 DNS、DoH (DNS over HTTPS) 上游服务器
- 内置 DNS 缓存
- 自定义 DNS 记录

### 代理服务
- **本地代理**
  - HTTP 代理
  - SOCKS5 代理
  - 透明代理 (tproxy)
  - TUN 模式
  - FakeDNS 支持
- **路由规则**
    - 基于端口的路由
    - 基于域名的路由
    - 基于 CIDR 的路由
    - 灵活的规则配置
- **代理协议**
  - VMess (支持 WebSocket)
  - VLESS
  - Trojan
  - Shadowsocks
  - SOCKS
  - SSH
  - WireGuard
  - 直连


### DDNS
- 动态 DNS 更新
- 支持多种 IP 获取方式（接口、HTTP、静态）



## 环境要求

- JDK 25 (需要 `--enable-preview` 支持)
- Gradle 9.x

## 快速开始

### 构建

```bash
./gradlew shadowJar
```

构建产物位于 `build/libs/ddnsp.jar`

### 运行

```bash
java -jar ddnsp.jar -c config.yaml
```

### Docker 运行

```bash
# 构建镜像
docker build -t ddnsp .

# 运行容器
docker run -d --privileged -v $(pwd)/config.yaml:/config.yaml ddnsp
```

## 配置说明

配置文件使用 YAML 格式，基本结构如下：

```yaml
logLevel: info  # 日志级别: trace, debug, info, warn, error

proxy:
  # HTTP 代理
  http:
    enable: true
    host: 0.0.0.0
    port: 8080
  
  # SOCKS5 代理
  socks:
    enable: true
    host: 0.0.0.0
    port: 1080
  
  # 透明代理
  transparent:
    enable: false
    host: 127.0.0.1
    port: 1081
  
  # TUN 模式
  tun:
    enable: false
    name: tun0
    mtu: 9000
    ignoreAddress: ["192.168.0.0/16"]

  # FakeDNS
  fakeDns:
    ipv4Pool: 11.0.0.0/8
    ipv6Pool: fd12:3456:789a:bcde::/64

  # NAT 映射
  nat:
    10.0.0.0/8: 192.168.1.1

  # 代理服务器列表
  proxies:
    - name: proxy1
      protocol: vmess
      host: example.com
      port: 443
      uid: your-uuid
      security: aes-128-gcm
      tls:
        enable: true
        serverName: example.com
      network: ws
      transport:
        ws:
          path: /

    - name: direct
      protocol: direct

  # 嗅探端口
  sniff:
    ports: [443, 8000-9000]

  # 路由规则
  rules:
    - port;53;direct
    - domain;example.com;proxy1
    - cidr;10.0.0.0/8;direct

dns:
  enable: true
  host: 0.0.0.0
  port: 53
  ttl: 300
  dnsServers:
    - dns://8.8.8.8:53
    - https://dns.google/dns-query
  records:
    - example.com. A 1.2.3.4

ddns:
  enable: false
```

## 支持的代理协议配置

### 公共配置项

所有代理协议支持以下公共配置：

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `name` | String | **必填**，代理名称，用于路由规则引用 |
| `protocol` | String | **必填**，协议类型 |
| `connIdle` | Integer | 连接空闲超时时间（秒），默认不限制 |
| `network` | String | 传输层协议：`tcp`、`ws` |
| `tls` | Object | TLS 配置 |

**TLS 配置：**

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| `enable` | Boolean | false | 是否启用 TLS |
| `allowInsecure` | Boolean | false | 是否允许不安全的证书 |
| `serverName` | String | - | TLS SNI 服务器名称 |
| `alpn` | String[] | - | ALPN 协议列表 |

**WebSocket 传输配置：**

```yaml
transport:
  ws:
    path: /
    headers:
      host: example.com
```

**网络设备绑定配置：**

```yaml
transport:
  dev: eth0  # 绑定网卡设备名
```

---

### Direct（直连）

内置协议，无需配置，直接连接目标地址。

```yaml
- name: direct
  protocol: direct
```

---

### Block（阻断）

内置协议，无需配置，阻断所有连接。用于配合路由规则屏蔽特定流量。

```yaml
- name: block
  protocol: block
```

在路由规则中使用：
```yaml
rules:
  - domain;ads.example.com;block
  - cidr;198.51.100.0/24;block
```

---

### VMess

```yaml
- name: vmess-proxy
  protocol: vmess
  host: example.com
  port: 443
  uid: uuid-string
  alterId: 0
  security: none
  connIdle: 300
  tls:
    enable: true
    allowInsecure: false
    serverName: example.com
  network: ws
  transport:
    ws:
      path: /
      headers:
        host: example.com
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `host` | String | 服务器地址 |
| `port` | Integer | 服务器端口 |
| `uid` | String | **必填**，用户 UUID |
| `alterId` | Integer | 额外 ID，默认 0 |
| `security` | String | 加密方式 |

**支持的加密方式：**
- `none` - 无加密（默认）
- `aes-128-gcm` - AES-128-GCM
- `chacha20-poly1305` - ChaCha20-Poly1305

---

### VLESS

```yaml
- name: vless-proxy
  protocol: vless
  host: example.com
  port: 443
  id: uuid-string
  flow: xtls-rprx-vision
  tls:
    enable: true
    serverName: example.com
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `host` | String | 服务器地址 |
| `port` | Integer | 服务器端口 |
| `id` | String | **必填**，用户 UUID |
| `flow` | String | 流控类型 |

---

### Trojan

```yaml
- name: trojan-proxy
  protocol: trojan
  host: example.com
  port: 443
  password: your-password
  tls:
    enable: true
    serverName: example.com
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `host` | String | 服务器地址 |
| `port` | Integer | 服务器端口 |
| `password` | String | **必填**，连接密码 |

---

### Shadowsocks

```yaml
- name: ss-proxy
  protocol: shadowsocks  # 或 ss
  host: example.com
  port: 8388
  cipher: aes-256-gcm
  password: your-password
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `host` | String | 服务器地址 |
| `port` | Integer | 服务器端口 |
| `cipher` | String | **必填**，加密方式 |
| `password` | String | **必填**，连接密码 |

**支持的加密方式：**

| 加密方式 | 说明 |
|---------|------|
| `aes-128-gcm` | AES-128-GCM |
| `aes-192-gcm` | AES-192-GCM |
| `aes-256-gcm` | AES-256-GCM |
| `chacha20-ietf-poly1305` | ChaCha20-IETF-Poly1305 |
| `2022-blake3-aes-128-gcm` | 2022 规范 AES-128-GCM |
| `2022-blake3-aes-256-gcm` | 2022 规范 AES-256-GCM |
| `2022-blake3-chacha20-poly1305` | 2022 规范 ChaCha20-Poly1305 |

---

### SOCKS

通过 SOCKS5 代理服务器进行连接。

```yaml
- name: socks-proxy
  protocol: socks
  host: proxy.example.com
  port: 1080
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `host` | String | **必填**，SOCKS 服务器地址 |
| `port` | Integer | **必填**，SOCKS 服务器端口 |

---

### WireGuard

```yaml
- name: wg0
  protocol: wg  # 或 wireguard
  address: 192.168.44.7/24
  dns: 192.168.44.1:53
  mtu: 1420
  privateKey: your-private-key
  peers:
    - publicKey: peer-public-key
      perSharedKey: preshared-key
      allowedIp: 0.0.0.0/0
      endpoint: server.example.com:51820
      keepAlive: 25
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `address` | String | **必填**，本地地址（CIDR 格式） |
| `privateKey` | String | **必填**，本地私钥 |
| `dns` | String | DNS 服务器地址 |
| `mtu` | Integer | MTU 值 |
| `peers` | Array | **必填**，对等节点列表 |

**Peer 配置：**

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `publicKey` | String | **必填**，对等节点公钥 |
| `perSharedKey` | String | **必填**，预共享密钥 |
| `allowedIp` | String | **必填**，允许的 IP 范围（CIDR 格式） |
| `endpoint` | String | **必填**，对等节点地址 |
| `keepAlive` | Integer | 心跳间隔（秒），0 表示不启用 |

---

### SSH

通过 SSH 隧道进行代理。

```yaml
- name: ssh-proxy
  protocol: ssh
  host: example.com
  port: 22
  user: username
  password: password
  # 或使用密钥认证
  # privateKey: /path/to/private_key
  # passphrase: key-passphrase
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `host` | String | **必填**，SSH 服务器地址 |
| `port` | Integer | SSH 服务器端口，默认 22 |
| `user` | String | **必填**，登录用户名 |
| `password` | String | 密码认证 |
| `privateKey` | String | 私钥文件路径 |
| `passphrase` | String | 私钥密码 |
| `serverKey` | String | 服务器公钥文件路径 |
| `verify` | String | 验证策略：`strict`、`accept_new`、`none` |

**认证方式说明：**
- **密码认证**：配置 `password`
- **密钥认证**：配置 `privateKey`，可选 `passphrase`
- 优先使用密钥认证，如果未配置则回退到密码认证

---

### Chain（代理链）

将多个代理串联使用，实现代理嵌套。

```yaml
# 方式一：引用已定义的代理名称
- name: chain1
  protocol: chain
  nodes:
    - proxy1
    - proxy2
    - direct

# 方式二：内联代理配置
- name: chain2
  protocol: chain
  nodes:
    - name: hop1
      protocol: socks
      host: socks1.example.com
      port: 1080
    - name: hop2
      protocol: vmess
      host: vmess.example.com
      port: 443
      uid: uuid-string
      security: aes-128-gcm
    - direct
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `nodes` | Array | **必填**，节点列表，支持代理名称或内联配置 |

**使用示例：**

```yaml
proxies:
  - name: socks-entry
    protocol: socks
    host: entry.example.com
    port: 1080

  - name: vmess-mid
    protocol: vmess
    host: mid.example.com
    port: 443
    uid: uuid-string
    tls:
      enable: true
      serverName: mid.example.com

  - name: my-chain
    protocol: chain
    nodes:
      - socks-entry
      - vmess-mid
      - direct
```

> **注意：** 节点按顺序执行，流量依次经过每个代理节点。

---

### LocalDNSForward（本地 DNS 转发）

内置协议，用于将 DNS 查询转发到本地 DNS 服务器。

```yaml
rules:
  - port;53;local-dns-forward
```

> 此协议为系统内置，无需在 proxies 中配置。


## 路由规则语法

规则格式：`类型;条件;目标代理`

系统按规则顺序进行匹配，首条匹配成功的规则生效。支持以下规则类型：

### 域名规则

| 规则类型 | 说明 | 示例 |
|---------|------|------|
| `domain` | 域名匹配，匹配主域名及其子域名（需要 `.` 分隔） | `domain;example.com;proxy1` |
| `eq` | 精确匹配域名，大小写不敏感 | `eq;www.example.com;proxy1` |
| `ew` | 域名后缀匹配，大小写不敏感 | `ew;.cn;proxy1` |
| `kw` | 域名包含关键字，大小写不敏感 | `kw;google;proxy1` |

**domain 规则详解：**
- `domain;example.com;proxy1` 匹配 `example.com`、`www.example.com`、`api.example.com`
- 不匹配 `notexample.com`（需要点号分隔）

**ew 后缀匹配示例：**
- `ew;.cn;proxy1` 匹配 `www.example.cn`、`test.cn`
- `ew;example.com;proxy1` 匹配 `example.com`、`api.example.com`

### IP/CIDR 规则

| 规则类型 | 说明 | 示例 |
|---------|------|------|
| `cidr` | 目标 IP CIDR 匹配，支持 IPv4/IPv6 | `cidr;10.0.0.0/8;direct` |
| `src-cidr` | 源 IP CIDR 匹配，支持 IPv4/IPv6 | `src-cidr;192.168.1.0/24;direct` |

**CIDR 示例：**
- `cidr;10.0.0.0/8;direct` - 匹配 10.0.0.0 - 10.255.255.255
- `cidr;192.168.1.0/24;direct` - 匹配 192.168.1.0 - 192.168.1.255
- `cidr;192.168.1.1/32;direct` - 精确匹配 192.168.1.1
- `cidr;fd00::/8;direct` - IPv6 地址匹配

### 端口规则

| 规则类型 | 说明 | 示例 |
|---------|------|------|
| `port` | 目标端口匹配 | `port;53;direct` |
| `src-port` | 源端口匹配 | `src-port;12345;direct` |

**端口范围：** 0-65535

### GeoIP 规则

| 规则类型 | 说明 | 示例 |
|---------|------|------|
| `geoip` | 基于 GeoIP 国家代码匹配（需要配置 MMDB 数据库） | `geoip;CN;direct` |

**GeoIP 示例：**
- `geoip;US;proxy1` - 匹配美国 IP
- `geoip;!US;proxy1` - 匹配非美国 IP

> 注意：使用 GeoIP 规则需要在配置文件中指定 MMDB 数据库路径：`mmdb: /path/to/GeoLite2-Country.mmdb`

### 默认规则

| 规则类型 | 说明 | 示例 |
|---------|------|------|
| `default` | 兜底规则，当其他规则都不匹配时使用 | `default;direct` |

### 规则组合示例

```yaml
rules:
  # DNS 流量走本地 DNS 转发
  - port;53;local-dns-forward
  
  # 国内域名直连
  - domain;baidu.com;direct
  - domain;qq.com;direct
  
  # 特定后缀域名走代理
  - ew;.io;proxy1
  - ew;.dev;proxy1
  
  # 关键字匹配
  - kw;github;proxy1
  
  # 国内 IP 直连
  - geoip;CN;direct
  
  # 内网 IP 直连
  - cidr;10.0.0.0/8;direct
  - cidr;172.16.0.0/12;direct
  - cidr;192.168.0.0/16;direct
  
  # 私有 IP 段直连
  - cidr;100.64.0.0/10;direct
  
```

### 规则逻辑

-  规则按配置顺序依次匹配
-  对于域名类规则，仅当目标地址为域名时生效

---

## DDNS 配置

DDNS（动态 DNS）功能用于自动更新域名的 DNS 记录，适用于公网 IP 动态变化的场景。

### 基本配置

```yaml
ddns:
  enable: true
  ipProviders:
    - name: my-ip
      src: http
      urls:
        - https://api.ipify.org
        - http://ip.3322.net/
  resolvers:
    - name: my-cf
      type: cf
      zoneId: your-zone-id
      apiToken: your-api-token
  domains:
    - name: home.example.com
      resolver: my-cf
      provider: my-ip
      ttl: 300
      interval: 300
      model: ip
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| `enable` | Boolean | false | 是否启用 DDNS |
| `ipProviders` | Array | - | IP 获取源配置列表 |
| `resolvers` | Array | - | DNS 解析服务配置列表 |
| `domains` | Array | - | 需要动态更新的域名列表 |

---

### IP 获取源（ipProviders）

用于获取当前公网 IP 地址。

#### HTTP 类型

通过 HTTP API 获取公网 IP。

```yaml
ipProviders:
  - name: my-http-ip
    src: http
    urls:
      - https://api.ipify.org
      - https://ipv4.lookup.test-ipv6.com/ip/
      - http://ip-api.com/json/?fields=query
      - http://ipinfo.io/ip
      - http://myip.ipip.net/
      - http://ip.3322.net/
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `name` | String | **必填**，提供器名称 |
| `src` | String | **必填**，值为 `http` |
| `urls` | String[] | HTTP API URL 列表，按顺序尝试 |

> 如果未配置 `urls` 或配置为空，将使用内置的默认 API 列表。

#### NIC 类型

从网络接口获取 IP 地址。

```yaml
ipProviders:
  - name: my-nic-ip
    src: nic
    interface: eth0
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `name` | String | **必填**，提供器名称 |
| `src` | String | **必填**，值为 `nic` |
| `interface` | String | **必填**，网络接口名称（如 `eth0`、`wlan0`） |

#### Static 类型

使用静态 IP 地址。

```yaml
ipProviders:
  - name: my-static-ip
    src: static
    ipv4: 1.2.3.4
    ipv6: 2001:db8::1
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `name` | String | **必填**，提供器名称 |
| `src` | String | **必填**，值为 `static` |
| `ipv4` | String | 静态 IPv4 地址（别名 `ip`） |
| `ipv6` | String | 静态 IPv6 地址 |

---

### DNS 解析服务（resolvers）

用于与 DNS 服务提供商 API 交互，更新 DNS 记录。

#### CloudFlare

```yaml
resolvers:
  - name: my-cf
    type: cf
    zoneId: your-cloudflare-zone-id
    apiToken: your-cloudflare-api-token
```

| 配置项 | 类型 | 说明 |
|-------|------|------|
| `name` | String | **必填**，解析器名称 |
| `type` | String | **必填**，值为 `cf` |
| `zoneId` | String | **必填**，CloudFlare Zone ID |
| `apiToken` | String | **必填**，CloudFlare API Token |

**获取 CloudFlare 配置信息：**

1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com)
2. 进入对应域名的概览页面
3. 在右侧"API"部分找到 **Zone ID**
4. 在 [API Tokens](https://dash.cloudflare.com/profile/api-tokens) 页面创建 Token（需要 DNS:Edit 权限）

---

### 域名配置（domains）

配置需要动态更新的域名。

```yaml
domains:
  # 更新 IPv4 和 IPv6
  - name: home.example.com
    resolver: my-cf
    provider: my-ip
    ttl: 300
    interval: 300
    model: ip

  # 仅更新 IPv4
  - name: ipv4.example.com
    resolver: my-cf
    provider: my-ip
    ttl: 300
    interval: 600
    model: ipv4

  # 仅更新 IPv6
  - name: ipv6.example.com
    resolver: my-cf
    provider: my-ip
    ttl: 300
    interval: 600
    model: ipv6
```

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| `name` | String | **必填**，要更新的域名 |
| `resolver` | String | **必填**，使用的解析器名称 |
| `provider` | String | `http` | 使用的 IP 提供器名称 |
| `ttl` | Integer | `300` | DNS 记录 TTL（秒） |
| `interval` | Integer | `86400` | 刷新检查间隔（秒） |
| `model` | String | `ip` | IP 模式 |

**IP 模式说明：**

| 值 | 说明 |
|----|------|
| `ip` | 同时更新 A（IPv4）和 AAAA（IPv6）记录 |
| `ipv4` | 仅更新 A（IPv4）记录 |
| `ipv6` | 仅更新 AAAA（IPv6）记录 |

---

### 完整配置示例

```yaml
ddns:
  enable: true
  
  # IP 获取源
  ipProviders:
    # 使用 HTTP API 获取公网 IP
    - name: http-ip
      src: http
      urls:
        - https://api.ipify.org
        - http://ip-api.com/json/?fields=query
    
    # 从网络接口获取 IP
    - name: eth0-ip
      src: nic
      interface: eth0
    
    # 静态 IP
    - name: static-ip
      src: static
      ipv4: 203.0.113.1
      ipv6: 2001:db8::1
  
  # DNS 解析服务
  resolvers:
    - name: cf-main
      type: cf
      zoneId: abc123...
      apiToken: your-api-token
  
  # 域名配置
  domains:
    # 主域名，同时更新 IPv4 和 IPv6
    - name: home.example.com
      resolver: cf-main
      provider: http-ip
      ttl: 300
      interval: 300
      mode: ip
    
    # 仅 IPv4
    - name: ipv4.example.com
      resolver: cf-main
      provider: eth0-ip
      ttl: 600
      interval: 600
      mode: ipv4
```
---


## 许可证

请查看 [LICENSE](LICENSE) 文件。


