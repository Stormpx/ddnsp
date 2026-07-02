# API 通用规范

ddnsp HTTP API 的通用约定。

## API 模块

| 模块 | 说明 | 文档 |
|---|---|---|
| 路由规则 | 覆盖规则、禁用规则、规则查看 | [routing-api.md](routing-api.md) |

---

## Base URL

```
http://<host>:<port>/api
```

---

## 认证

所有请求需携带 Header：

```
Authorization: Bearer <token>
```

Token 在配置文件中指定。

---

## 响应格式

所有接口返回 JSON，HTTP 状态码固定 `200`，通过 `code` 区分结果。

**成功：**

```json
{
  "code": 0,
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | number | `0` 表示成功 |
| `data` | any | 业务数据，结构因接口而异 |

**失败：**

```json
{
  "code": 400,
  "message": "invalid rule: xxx"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | number | 错误码 |
| `message` | string | 错误描述 |

### 错误码

| code | 含义 |
|---|---|
| `0` | 成功 |
| `400` | 请求参数错误 |
| `401` | 未认证 |
| `404` | 资源不存在 |
| `500` | 服务器内部错误 |

---

## 传参方式

| 方法 | 传参方式 | Content-Type |
|---|---|---|
| GET | 无请求体 | — |
| POST | JSON Body | `application/json` |
| DELETE | 路径参数 `{id}` 或 JSON Body | `application/json` |
