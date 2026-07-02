# Routing Management API

管理 ddnsp 路由规则的 HTTP API。规则分为三层：

1. **Override 层** — 通过 API 新增的规则，优先匹配
2. **Base 层** — 配置文件中的原始规则
3. **Disabled 集合** — 被禁用的底层规则，不参与匹配

匹配顺序：Override 层先匹配，匹配成功直接返回；全部未命中再匹配 Base 层（排除已禁用的规则）。

---

## 规则格式

规则使用分号分隔的字符串：`type;content;tag`

| type | 含义 | 示例 |
|---|---|---|
| `eq` | 域名精确匹配 | `eq;example.com;proxy` |
| `ew` | 域名后缀匹配 | `ew;.google.com;proxy` |
| `kw` | 域名关键字匹配 | `kw;google;proxy` |
| `domain` | 子域名匹配（点边界） | `domain;google.com;proxy` |
| `cidr` | 目标 IP CIDR 匹配 | `cidr;10.0.0.0/8;direct` |
| `src-cidr` | 源 IP CIDR 匹配 | `src-cidr;192.168.1.0/24;direct` |
| `port` | 目标端口匹配 | `port;443;proxy` |
| `src-port` | 源端口匹配 | `src-port;12345;direct` |
| `geoip` | GeoIP 国家匹配 | `geoip;CN;proxy` / `geoip;!CN;direct` |
| `default` | 默认规则（兜底） | `default;;direct` |

---

## Override 规则管理

### GET /api/rules/override

列出所有覆盖规则。

**响应：**
```json
{
  "rules": [
    { "id": "a1b2c3", "rule": "domain;example.com;proxy" },
    { "id": "d4e5f6", "rule": "cidr;172.16.0.0/12;direct" }
  ]
}
```

### POST /api/rules/override

新增一条覆盖规则。

**请求：**
```json
{
  "rule": "domain;example.com;proxy"
}
```

**响应：**
```json
{
  "id": "a1b2c3",
  "rule": "domain;example.com;proxy"
}
```

**错误：**
- `400` — 规则格式无效

### DELETE /api/rules/override/{id}

删除指定 ID 的覆盖规则。

**响应：**
```json
{
  "success": true
}
```

**错误：**
- `404` — 规则不存在

---

## 禁用底层规则

### GET /api/rules/disabled

列出所有被禁用的底层规则。

**响应：**
```json
{
  "rules": [
    "domain;google.com;direct",
    "cidr;10.0.0.0/8;direct"
  ]
}
```

### POST /api/rules/disable

禁用一条底层规则。`rule` 为底层规则的完整字符串（与配置文件中一致）。

**请求：**
```json
{
  "rule": "domain;google.com;direct"
}
```

**响应：**
```json
{
  "success": true
}
```

**错误：**
- `400` — 底层规则中不存在该规则

### DELETE /api/rules/disable

取消禁用一条底层规则。

**请求：**
```json
{
  "rule": "domain;google.com;direct"
}
```

**响应：**
```json
{
  "success": true
}
```

**错误：**
- `400` — 该规则未被禁用

---

## 完整规则链

### GET /api/rules/all

查看完整规则链，用于调试。返回三个字段分别对应三层状态。

**响应：**
```json
{
  "override": [
    { "id": "a1b2c3", "rule": "domain;example.com;proxy" }
  ],
  "base": [
    "domain;google.com;direct",
    "cidr;10.0.0.0/8;direct",
    "geoip;CN;proxy",
    "default;;direct"
  ],
  "disabled": [
    "domain;google.com;direct"
  ]
}
```

- **override** — API 新增的覆盖规则（带 ID）
- **base** — 配置文件中的全部底层规则（未过滤的完整列表）
- **disabled** — 当前被禁用的底层规则指纹
