# ChronoSync API（客户端对接说明）

服务端 Swagger：`http://115.190.155.26:8000/docs`

## 基本信息

- Base URL：`http://115.190.155.26:8000`
- OpenAPI：`GET /openapi.json`
- 认证方式：OAuth2 Password + JWT
  - 登录接口返回 `access_token`
  - 后续请求统一带 Header：`Authorization: Bearer <token>`

## 快速开始（curl）

### 1）注册

`POST /api/v1/auth/register`（JSON）

```bash
curl -X POST 'http://115.190.155.26:8000/api/v1/auth/register' \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"pass1234"}'
```

响应示例（201）：

```json
{"id":"...","username":"demo","created_at":"...","updated_at":"..."}
```

### 2）登录

`POST /api/v1/auth/login`（`application/x-www-form-urlencoded`）

```bash
curl -X POST 'http://115.190.155.26:8000/api/v1/auth/login' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=demo&password=pass1234'
```

响应示例（200）：

```json
{"access_token":"<jwt>","token_type":"bearer","expires_in":1800}
```

## 接口清单

### 健康检查

- `GET /health`：服务存活
- `GET /api/v1/ping`：服务存活（v1）

### Providers

- `GET /api/v1/providers/`
  - 返回数组：`[{"id":"tongyi","name":"通义","capabilities":[...]}]`

### Events（日程）

#### 列表

- `GET /api/v1/events/`
  - Query：
    - `start_date`（可选，建议 `yyyy-MM-dd`）
    - `end_date`（可选，建议 `yyyy-MM-dd`）
    - `page`（默认 1）
    - `size`（默认 20）
  - 返回示例：
    - `{"items":[...],"total":1,"page":1,"size":20}`

#### 新建

- `POST /api/v1/events/`
  - Body（JSON）：
    - `title`（string，必填）
    - `start_time`（string，必填，ISO-8601，如 `2026-03-06T10:00:00+08:00`）
    - `end_time`（string，可选）
    - `description`（string，可选）
    - `location`（string，可选）

#### 详情/更新/删除

- `GET /api/v1/events/{event_id}`
- `PUT /api/v1/events/{event_id}`（部分字段可选：`title/description/start_time/end_time/location/status`）
- `DELETE /api/v1/events/{event_id}`
- `PATCH /api/v1/events/{event_id}/status`（仅更新状态）

### Memos（备忘录）

#### 列表

- `GET /api/v1/memos/`
  - Query：`page`（默认 1）、`size`（默认 20）
  - 返回示例：`{"items":[...],"total":1,"page":1,"size":20}`

#### 新建/更新/删除

- `POST /api/v1/memos/`
  - Body：`{"content":"...","tags":["todo"]}`（tags 可选）
- `GET /api/v1/memos/{memo_id}`
- `PUT /api/v1/memos/{memo_id}`（可更新 `content/tags`）
- `DELETE /api/v1/memos/{memo_id}`

### Agent（自然语言处理）

- `POST /api/v1/agent/process`
  - Body：`{"text":"...","conversation_id":null}`
  - Response：`{"action":"...","entity":"...","data":{},"reply":"..."}`

### Sync（批量同步，占位）

- `POST /api/v1/sync/events/`：批量同步请求（目前为临时实现）
- `GET /api/v1/sync/events/`：增量同步（目前占位，返回空）

## Android 客户端约定（本项目）

为兼容现有 UI（需要 `type/priority`、Memo 的 `title/content` 分离展示），客户端在服务端字段中做了轻量封装：

- `Event.description`：存储客户端元信息（JSON），形如：
  - `{"_chronosync_meta":{"type":"WORK","priority":3}}`
- `Memo.content`：存储 `{title, content}` 的 JSON 字符串，形如：
  - `{"title":"xxx","content":"yyy"}`

客户端实现入口：`app/src/main/java/com/example/chronosyncapp/network/ChronoSyncApi.kt`

