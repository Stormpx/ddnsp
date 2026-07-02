# Routing API

管理路由规则。通用规范见 [api.md](api.md)。

## 概念

规则分三层，匹配顺序从上到下：

| 层 | 来源 | 说明 |
|---|---|---|
| Override | API 新增 | 优先匹配，命中即返回 |
| Base | 配置文件 | Override 全部未命中时匹配 |
| Disabled | API 禁用 | Base 中被禁用的规则，跳过匹配 |

---

## 规则格式

所有规则为 `type;content;tag` 格式的字符串，分号分隔。

| type | content 含义 | 示例 |
|---|---|---|
| `eq` | 域名精确匹配 | `eq;example.com;proxy` |
| `ew` | 域名后缀匹配 | `ew;.google.com;proxy` |
| `kw` | 域名包含关键字 | `kw;google;proxy` |
| `domain` | 子域名匹配（点边界） | `domain;google.com;proxy` |
| `cidr` | 目标 IP 段 | `cidr;10.0.0.0/8;direct` |
| `src-cidr` | 源 IP 段 | `src-cidr;192.168.1.0/24;direct` |
| `port` | 目标端口 | `port;443;proxy` |
| `src-port` | 源端口 | `src-port;12345;direct` |
| `geoip` | GeoIP 国家（支持 `!` 取反） | `geoip;CN;proxy` |
| `default` | 兜底规则 | `default;;direct` |

---

## Override 规则

### GET /api/rules/override

列出所有覆盖规则。

**响应 data** — `Array<OverrideRule>`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 规则 ID（由服务端生成） |
| `rule` | string | 规则内容 |

```json
[
  { "id": "a1b2c3", "rule": "domain;example.com;proxy" },
  { "id": "d4e5f6", "rule": "cidr;172.16.0.0/12;direct" }
]
```

### POST /api/rules/override

新增覆盖规则。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `rule` | string | 是 | 规则内容（`type;content;tag`） |

```json
{ "rule": "domain;example.com;proxy" }
```

**响应 data** — `OverrideRule`

```json
{ "id": "a1b2c3", "rule": "domain;example.com;proxy" }
```

**错误**

| code | 说明 |
|---|---|
| `400` | 规则格式无效 |

### DELETE /api/rules/override/{id}

删除覆盖规则。

**路径参数**

| 参数 | 说明 |
|---|---|
| `id` | 规则 ID |

**响应 data** — `null`

**错误**

| code | 说明 |
|---|---|
| `404` | 规则不存在 |

---

## 禁用底层规则

### GET /api/rules/disabled

列出所有被禁用的底层规则。

**响应 data** — `Array<string>`

```json
[
  "domain;google.com;direct",
  "cidr;10.0.0.0/8;direct"
]
```

### POST /api/rules/disable

禁用一条底层规则。`rule` 必须与 Base 层中的规则字符串完全一致。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `rule` | string | 是 | 底层规则的完整字符串 |

```json
{ "rule": "domain;google.com;direct" }
```

**响应 data** — `null`

**错误**

| code | 说明 |
|---|---|
| `400` | Base 层中不存在该规则 |

### DELETE /api/rules/disable

取消禁用一条底层规则。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `rule` | string | 是 | 要取消禁用的规则字符串 |

```json
{ "rule": "domain;google.com;direct" }
```

**响应 data** — `null`

**错误**

| code | 说明 |
|---|---|
| `400` | 该规则未被禁用 |

---

## 完整规则链

### GET /api/rules/all

查看三层规则的完整状态，用于调试。

**响应 data**

| 字段 | 类型 | 说明 |
|---|---|---|
| `override` | `Array<OverrideRule>` | 覆盖规则列表 |
| `base` | `Array<string>` | Base 层全部规则（未过滤） |
| `disabled` | `Array<string>` | 当前被禁用的规则（base 的子集） |

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
