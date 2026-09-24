# DayTrace 接口文档

版本：v0.1 · 状态：接口设计稿，尚未实现。

本文件参考用户提供的 Tlias 接口文档编排方式，按“基本信息、请求参数、响应数据、备注说明”组织。接口内容来自 DayTrace 的[需求边界](requirements-boundary.md)和[系统架构](architecture.md)，不复制参考系统的业务功能或认证机制。

当前源码包含 Spring Boot 启动类、上下文加载测试及独立的 hello 学习接口；本文列出的正式接口均未实现。hello 练习的默认 400 校验响应和简单对象响应尚未对齐本文契约。示例为虚构数据，并非真实调用结果。新增的字段长度、分页上限、错误码等为本轮具体设计建议，可在实现前讨论调整。本文不代表已完成正式业务安全、模型效果或接口测试。

## 0. 通用约定

### 0.1 地址与数据格式

- 本地示例服务地址：`http://localhost:8081`，以实际启动端口为准。
- 浏览器业务接口前缀统一为 `/api/v1`；下文每个接口都写完整路径。
- JSON 请求使用 `Content-Type: application/json`，UTF-8 编码；无请求体的接口不发送空 JSON。
- ID 在 JSON、路径和查询中均使用十进制字符串，避免 Java Long 与 JavaScript 数值精度差异。
- 字段采用 camelCase；未定义的写入字段拒绝为 400，防止无意修改 userId 等内部字段。
- 日期为 `YYYY-MM-DD`；时刻为带偏移的 ISO 8601 字符串，响应统一展示 +08:00。服务端按真实时刻比较。
- 创建／修改接口中标为“是”的可空字段必须出现，无值时传 null；集合无元素时传 []。
- PUT 表示完整更新可编辑字段，不以字段缺失表示“不修改”。文中字符长度按 Unicode 码点计算，密码另设 UTF-8 字节限制。
- 列表响应统一为 `{total, rows, page, pageSize}`；无结果返回 total=0、rows=[]，超出末页也返回空 rows。
- 查询参数类型错误、未知参数或枚举、无效日期为 400；合法 JSON 但业务字段不满足条件为 422。

### 0.2 登录、CSRF 与隐私

本设计使用服务端 Session。登录成功通过 Set-Cookie 建立／更新 JSESSIONID，不在 JSON 中返回登录 token。生产 Cookie 使用 HttpOnly、Secure、SameSite=Lax；本机 HTTP 开发时 Secure 配置按环境调整。

首次访问先 GET /api/v1/auth/csrf，浏览器保存对应会话 Cookie；包括注册和登录在内的所有 POST／PUT／DELETE 请求均携带返回的 CSRF 请求头。登录成功、退出或修改密码后重新取得 CSRF token。CSRF token 可由前端读取，登录 Cookie 不可由前端脚本读取。

除获取 CSRF、注册和登录外，业务接口均要求登录。身份取自服务端会话，不接受请求 userId 决定所有者；其他用户的资源与不存在的资源统一返回 404。任何响应不包含密码、密码哈希、会话 ID、模型密钥或其他用户内容。

鉴权与错误处理顺序：需要登录的接口先返回未登录 401，再验证 CSRF（失败 403），之后进行资源权限与业务校验。该顺序是实现目标，需在 Spring Security 配置及测试中验证。登录失败不能通过 message 区分账号不存在与密码错误。

### 0.3 统一响应

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| code | string | 是 | 成功为 SUCCESS；失败为第 7 节的业务错误码 |
| message | string | 是 | 给用户看的简短说明；前端判断逻辑使用 code |
| data | object/array/null | 是 | 业务数据；无数据为 null |
| requestId | string | 是 | 服务端生成的本次请求追踪 ID，与日程 creationRequestId 不同 |
| fieldErrors | object[] | 否 | 校验失败时提供，每项包含 field 和 message |

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": null,
  "requestId": "req-demo-001"
}
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请检查填写内容",
  "data": null,
  "requestId": "req-demo-002",
  "fieldErrors": [
    {
      "field": "endAt",
      "message": "结束时间必须晚于开始时间"
    }
  ]
}
```

HTTP 状态码与 code 配合使用，不把所有失败包装成 HTTP 200。成功查询／更新／删除／AI 返回 200，首次创建资源返回 201；日程创建幂等重放返回 200。删除也返回上述 JSON，不使用 204。

### 0.4 公共响应对象

#### User：账号信息

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 当前用户 ID |
| username | string | 是 | 规范化用户名 |
| createdAt | string | 是 | 创建时刻 |

#### Tag：标签

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 标签 ID |
| name | string | 是 | 标签名称 |

#### Record：记录详情

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 记录 ID |
| type | string | 是 | DIARY／NOTE |
| title | string | 是 | 标题 |
| content | string | 是 | Markdown 正文 |
| recordDate | string/null | 是 | 日记日期；笔记为 null |
| tags | Tag[] | 是 | 标签列表 |
| version | integer | 是 | 初始 1，每次状态或内容修改递增 |
| deletedAt | string/null | 是 | 回收站删除时间；正常记录为 null |
| createdAt、updatedAt | string | 是 | 创建、修改时刻 |

列表返回 RecordSummary：包含 Record 中除 content 之外的所有字段，额外返回 contentPreview（string，正文前最多 120 字符，作为纯文本显示）。正文仅通过详情接口读取。

#### Event：日程详情

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id、title | string | 是 | 日程 ID、标题 |
| description、location | string/null | 是 | 描述、地点 |
| timeKind | string | 是 | TIMED／ALL_DAY |
| startAt、endAt | string/null | 是 | 定时日程的起止时刻 |
| startDate、endDateExclusive | string/null | 是 | 全天日程的日期范围，右端不包含 |
| sourceRecordId | string/null | 是 | 来源记录 ID，手动创建或来源永久删除后为空 |
| sourceRecordState | string | 是 | ACTIVE／TRASHED／UNAVAILABLE；最后一种含无来源或已永久删除 |
| version | integer | 是 | 初始 1，修改递增 |
| createdAt、updatedAt | string | 是 | 创建、修改时刻 |

日程来源状态由服务端计算，只返回当前用户的来源信息，不附带来源正文。来源进入回收站不改变日程内容；永久删除来源时来源 ID 置空且日程 version 递增。

#### Warning：日程提示

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| type | string | 是 | TIME_CONFLICT 或 POSSIBLE_DUPLICATE |
| message | string | 是 | 提示说明 |
| eventIds | string[] | 是 | 关联的当前用户日程 ID，最多 20 项 |
| total | integer | 是 | 匹配总数；可能大于 eventIds.length |

#### Candidate：AI 候选日程

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| candidateKey | string | 是 | 仅在本次提取结果中唯一，不是数据库 ID |
| title | string/null | 是 | 识别的标题，缺失可为 null |
| description、location | string/null | 是 | 描述、地点，无信息为 null |
| timeKind | string/null | 是 | TIMED／ALL_DAY，无法判断时 null |
| startAt、endAt | string/null | 是 | 明确的起止时刻；不确定或缺失为 null |
| startDate、endDateExclusive | string/null | 是 | 明确的全天日期范围，否则 null |
| evidenceText | string | 是 | 来自本次正文的原文片段 |
| missingFields | string[] | 是 | 需要补充的字段名 |
| uncertaintyReasons | string[] | 是 | 如时间待定、安排仅为可能；无则 [] |

### 0.5 版本与创建幂等

记录和日程更新均携带 version；删除／恢复通过 Query 携带 version。版本落后返回 VERSION_CONFLICT（409），不能覆盖更新。不存在资源仍返回 404。

日程创建用 creationRequestId（前端生成 UUID）：同一用户、同一 ID、同一规范化业务内容返回原日程 ID，绝不再次创建；相同 ID 携带不同业务内容返回 IDEMPOTENCY_CONFLICT。返回日程的当前状态，若原日程已删除则返回 ORIGINAL_EVENT_DELETED，不复活。重放先于来源版本与冲突检查，但始终先验证身份。

哈希包含标题、描述、地点、时间类型、归一化时间及来源 ID／版本；不包含 acceptWarnings 或链路 requestId。空白处理按字段规则，TIMED 归一为 UTC；所有可空字段显式为 null。首次确认提示未完成不创建凭据。仅网络重试或接受已有提示可复用 creationRequestId；用户修改了业务字段就生成新 ID。其他写接口不承诺创建幂等，前端不得自动重试未知结果。

## 接口目录

| 编号 | 接口 | 方法 | 路径 |
| --- | --- | --- | --- |
| 1.1 | 获取 CSRF token | GET | `/api/v1/auth/csrf` |
| 1.2 | 注册账号 | POST | `/api/v1/auth/register` |
| 1.3 | 登录 | POST | `/api/v1/auth/login` |
| 1.4 | 查询当前用户 | GET | `/api/v1/auth/me` |
| 1.5 | 退出登录 | POST | `/api/v1/auth/logout` |
| 1.6 | 修改密码 | PUT | `/api/v1/auth/password` |
| 2.1 | 记录分页查询／回收站查询 | GET | `/api/v1/records` |
| 2.2 | 查询记录详情 | GET | `/api/v1/records/{id}` |
| 2.3 | 新增日记／笔记 | POST | `/api/v1/records` |
| 2.4 | 修改记录 | PUT | `/api/v1/records/{id}` |
| 2.5 | 将记录移入回收站 | DELETE | `/api/v1/records/{id}` |
| 2.6 | 恢复记录 | POST | `/api/v1/records/{id}/restore` |
| 2.7 | 永久删除记录 | DELETE | `/api/v1/records/{id}/permanent` |
| 3.1 | 标签分页查询 | GET | `/api/v1/tags` |
| 3.2 | 新增标签 | POST | `/api/v1/tags` |
| 3.3 | 修改标签 | PUT | `/api/v1/tags/{id}` |
| 3.4 | 删除标签 | DELETE | `/api/v1/tags/{id}` |
| 4.1 | 日历范围查询 | GET | `/api/v1/events` |
| 4.2 | 查询日程详情 | GET | `/api/v1/events/{id}` |
| 4.3 | 创建日程／确认 AI 候选 | POST | `/api/v1/events` |
| 4.4 | 修改日程 | PUT | `/api/v1/events/{id}` |
| 4.5 | 删除日程 | DELETE | `/api/v1/events/{id}` |
| 5.1 | 根据要点整理成文 | POST | `/api/v1/ai/compose` |
| 5.2 | 润色选中文字 | POST | `/api/v1/ai/polish` |
| 5.3 | 从记录提取候选日程 | POST | `/api/v1/records/{id}/extract-events` |
| 6.1 | 内部要点整理 | POST | `/internal/v1/compose` |
| 6.2 | 内部文本润色 | POST | `/internal/v1/polish` |
| 6.3 | 内部日程提取 | POST | `/internal/v1/extract-events` |

## 1. 账号管理

### 1.1 获取 CSRF token

#### 1.1.1 基本信息

> 请求路径：/api/v1/auth/csrf
>
> 请求方式：GET
>
> 接口描述：为当前匿名或登录会话取得写请求所需 token。
>
> 登录要求：无需登录
>
> 实现状态：未实现（设计稿）

#### 1.1.2 请求参数

参数格式：无。

无业务请求参数。

#### 1.1.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 CSRF 配置。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| headerName | string | 是 | 固定 X-CSRF-TOKEN |
| token | string | 是 | 当前 CSRF token，示例不可用于实际请求 |

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "headerName": "X-CSRF-TOKEN",
    "token": "csrf-example-only"
  },
  "requestId": "req-demo-001"
}
```

#### 1.1.4 备注说明

响应禁止缓存。此接口可创建匿名会话，前端保留 Cookie；已有登录会话不会因此退出。

### 1.2 注册账号

#### 1.2.1 基本信息

> 请求路径：/api/v1/auth/register
>
> 请求方式：POST
>
> 接口描述：创建账号，成功后由用户登录。
>
> 登录要求：无需登录；需要匿名会话 CSRF
>
> 实现状态：未实现（设计稿）

#### 1.2.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 3～32 位字母、数字、下划线；首字母为字母；去首尾空白后转小写，唯一 |
| password | string | 是 | 8～64 字符，同时包含字母和数字；UTF-8 不超过 72 字节，不裁剪空格 |

请求参数样例：

```json
{
  "username": "daytrace_demo",
  "password": "DemoOnly2026!"
}
```

#### 1.2.3 响应数据

HTTP 201，application/json；通用外层字段见 0.3。

data 为 User。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "1",
    "username": "daytrace_demo",
    "createdAt": "2026-09-24T10:00:00+08:00"
  },
  "requestId": "req-demo-001"
}
```

#### 1.2.4 备注说明

USERNAME_EXISTS → 409；密码规则不满足 → 422。响应和日志均不返回密码；注册不自动登录。

### 1.3 登录

#### 1.3.1 基本信息

> 请求路径：/api/v1/auth/login
>
> 请求方式：POST
>
> 接口描述：验证账号密码并建立登录会话。
>
> 登录要求：无需登录；需要匿名会话 CSRF
>
> 实现状态：未实现（设计稿）

#### 1.3.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 按注册规则规范化 |
| password | string | 是 | 原样提交，不裁剪 |

请求参数样例：

```json
{
  "username": "daytrace_demo",
  "password": "DemoOnly2026!"
}
```

#### 1.3.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 User；登录会话通过 Set-Cookie 返回。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "1",
    "username": "daytrace_demo",
    "createdAt": "2026-09-24T10:00:00+08:00"
  },
  "requestId": "req-demo-001"
}
```

#### 1.3.4 备注说明

失败返回 INVALID_CREDENTIALS（401）。登录后重新获取 CSRF token；请求限流为 RATE_LIMITED（429）。

### 1.4 查询当前用户

#### 1.4.1 基本信息

> 请求路径：/api/v1/auth/me
>
> 请求方式：GET
>
> 接口描述：读取当前登录账号信息。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 1.4.2 请求参数

参数格式：无。

无业务请求参数。

#### 1.4.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 User。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "1",
    "username": "daytrace_demo",
    "createdAt": "2026-09-24T10:00:00+08:00"
  },
  "requestId": "req-demo-001"
}
```

### 1.5 退出登录

#### 1.5.1 基本信息

> 请求路径：/api/v1/auth/logout
>
> 请求方式：POST
>
> 接口描述：使当前会话失效并清除登录 Cookie。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 1.5.2 请求参数

参数格式：无请求体。

无业务请求参数。

#### 1.5.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 null。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": null,
  "requestId": "req-demo-001"
}
```

#### 1.5.4 备注说明

只退出当前会话。前端清空用户与私人页面状态，再重新获取匿名 CSRF。已经失效的登录态按通用规则返回 401。

### 1.6 修改密码

#### 1.6.1 基本信息

> 请求路径：/api/v1/auth/password
>
> 请求方式：PUT
>
> 接口描述：验证旧密码并更新密码，使该用户的旧会话失效。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 1.6.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| oldPassword | string | 是 | 原密码 |
| newPassword | string | 是 | 与注册密码规则相同，不能与旧密码相同 |

请求参数样例：

```json
{
  "oldPassword": "DemoOnly2026!",
  "newPassword": "NewDemoOnly2026!"
}
```

#### 1.6.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 null，成功后需重新登录。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": null,
  "requestId": "req-demo-001"
}
```

#### 1.6.4 备注说明

旧密码错误返回 OLD_PASSWORD_INVALID（422）。成功后包括当前会话在内的旧会话均失效；其他会话最迟下次请求时被拒绝。

## 2. 日记与笔记管理

### 2.1 记录分页查询／回收站查询

#### 2.1.1 基本信息

> 请求路径：/api/v1/records
>
> 请求方式：GET
>
> 接口描述：按条件查询本人记录列表，不返回完整正文。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 2.1.2 请求参数

参数格式：queryString。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| page | integer | 否 | Query；默认 1，最小 1 |
| pageSize | integer | 否 | Query；默认 20，范围 1～100 |
| type | string | 否 | DIARY／NOTE，不传则两者 |
| keyword | string | 否 | 去首尾空白，最长 100；按标题或正文包含搜索；% 和 _ 视为普通字符 |
| tagId | string | 否 | 按本人标签筛选，不属于本人或不存在则 404 |
| recordDateFrom、recordDateTo | string | 否 | 日记日期闭区间；必须同时提供并指定 type=DIARY |
| deleted | boolean | 否 | 默认 false；true 查询回收站 |

请求参数样例：

```http
/api/v1/records?type=DIARY&page=1&pageSize=20&deleted=false
```

#### 2.1.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为分页对象，rows 为 RecordSummary[]。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": "101",
        "type": "DIARY",
        "title": "今天的学习记录",
        "recordDate": "2026-09-24",
        "tags": [
          {
            "id": "11",
            "name": "毕设"
          }
        ],
        "version": 1,
        "deletedAt": null,
        "createdAt": "2026-09-24T20:00:00+08:00",
        "updatedAt": "2026-09-24T20:00:00+08:00",
        "contentPreview": "今天确定了毕设方向，明天下午三点到四点和导师讨论。"
      }
    ],
    "page": 1,
    "pageSize": 20
  },
  "requestId": "req-demo-001"
}
```

#### 2.1.4 备注说明

默认按 updatedAt 降序、id 降序；回收站按 deletedAt 降序、id 降序。所有过滤条件取交集；日期起始不得晚于结束。

### 2.2 查询记录详情

#### 2.2.1 基本信息

> 请求路径：/api/v1/records/{id}
>
> 请求方式：GET
>
> 接口描述：查看本人正常记录或回收站记录详情。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 2.2.2 请求参数

参数格式：path。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |

请求参数样例：

```http
/api/v1/records/101
```

#### 2.2.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 Record。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "101",
    "type": "DIARY",
    "title": "今天的学习记录",
    "content": "今天确定了毕设方向，明天下午三点到四点和导师讨论。",
    "recordDate": "2026-09-24",
    "tags": [
      {
        "id": "11",
        "name": "毕设"
      }
    ],
    "version": 1,
    "deletedAt": null,
    "createdAt": "2026-09-24T20:00:00+08:00",
    "updatedAt": "2026-09-24T20:00:00+08:00"
  },
  "requestId": "req-demo-001"
}
```

#### 2.2.4 备注说明

回收站内容可阅读但不得编辑或用于 AI 提取。不存在或其他用户记录返回 404。

### 2.3 新增日记／笔记

#### 2.3.1 基本信息

> 请求路径：/api/v1/records
>
> 请求方式：POST
>
> 接口描述：保存一篇新记录。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 2.3.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| type | string | 是 | DIARY 或 NOTE |
| title | string | 是 | 去除首尾空白后 1～100 字符 |
| content | string | 是 | Markdown 正文；允许空字符串，最长 20000 字符 |
| recordDate | string/null | 是 | DIARY 必须为 YYYY-MM-DD；NOTE 必须为 null |
| tagIds | string[] | 是 | 当前用户标签 ID；无标签传 []，最多 20 个，不重复 |

请求参数样例：

```json
{
  "type": "DIARY",
  "title": "今天的学习记录",
  "content": "今天确定了毕设方向，明天下午三点到四点和导师讨论。",
  "recordDate": "2026-09-24",
  "tagIds": [
    "11"
  ]
}
```

#### 2.3.3 响应数据

HTTP 201，application/json；通用外层字段见 0.3。

data 为 Record，初始 version=1。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "101",
    "type": "DIARY",
    "title": "今天的学习记录",
    "content": "今天确定了毕设方向，明天下午三点到四点和导师讨论。",
    "recordDate": "2026-09-24",
    "tags": [
      {
        "id": "11",
        "name": "毕设"
      }
    ],
    "version": 1,
    "deletedAt": null,
    "createdAt": "2026-09-24T20:00:00+08:00",
    "updatedAt": "2026-09-24T20:00:00+08:00"
  },
  "requestId": "req-demo-001"
}
```

#### 2.3.4 备注说明

同一天允许多篇日记；全部 tagIds 必须存在且归当前用户，否则返回 404，整体不保存。NOTE 的 recordDate 必须为 null。

### 2.4 修改记录

#### 2.4.1 基本信息

> 请求路径：/api/v1/records/{id}
>
> 请求方式：PUT
>
> 接口描述：完整更新本人正常记录及标签关联。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 2.4.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |
| type | string | 是 | DIARY 或 NOTE |
| title | string | 是 | 去除首尾空白后 1～100 字符 |
| content | string | 是 | Markdown 正文；允许空字符串，最长 20000 字符 |
| recordDate | string/null | 是 | DIARY 必须为 YYYY-MM-DD；NOTE 必须为 null |
| tagIds | string[] | 是 | 当前用户标签 ID；无标签传 []，最多 20 个，不重复 |
| version | integer | 是 | 当前读取到的版本号，最小 1；不匹配返回 409 |

请求参数样例：

```json
{
  "type": "DIARY",
  "title": "今天的学习记录",
  "content": "今天确定了毕设方向，明天下午三点到四点和导师讨论。",
  "recordDate": "2026-09-24",
  "tagIds": [
    "11"
  ],
  "version": 1
}
```

#### 2.4.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为更新后的 Record。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "101",
    "type": "DIARY",
    "title": "今天的学习记录",
    "content": "今天确定了毕设方向，明天下午三点到四点和导师讨论。",
    "recordDate": "2026-09-24",
    "tags": [
      {
        "id": "11",
        "name": "毕设"
      }
    ],
    "version": 2,
    "deletedAt": null,
    "createdAt": "2026-09-24T20:00:00+08:00",
    "updatedAt": "2026-09-24T20:10:00+08:00"
  },
  "requestId": "req-demo-001"
}
```

#### 2.4.4 备注说明

版本过期返回 VERSION_CONFLICT（409）；回收站记录返回 RECORD_IN_TRASH（409）。允许切换类型，但必须同步满足 recordDate 规则。不修改已生成日程。

### 2.5 将记录移入回收站

#### 2.5.1 基本信息

> 请求路径：/api/v1/records/{id}
>
> 请求方式：DELETE
>
> 接口描述：软删除记录，仍可恢复。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 2.5.2 请求参数

参数格式：path + queryString，无请求体。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |
| version | integer | 是 | Query；当前记录版本 |

请求参数样例：

```http
/api/v1/records/101?version=1
```

#### 2.5.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 null。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": null,
  "requestId": "req-demo-001"
}
```

#### 2.5.4 备注说明

设置 deletedAt 并递增 version；来源日程保留。重复删除已入回收站记录返回 RECORD_IN_TRASH（409），版本不匹配先返回 VERSION_CONFLICT。

### 2.6 恢复记录

#### 2.6.1 基本信息

> 请求路径：/api/v1/records/{id}/restore
>
> 请求方式：POST
>
> 接口描述：恢复本人回收站记录。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 2.6.2 请求参数

参数格式：path + queryString，无请求体。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |
| version | integer | 是 | Query；回收站中当前版本 |

请求参数样例：

```http
/api/v1/records/101/restore?version=2
```

#### 2.6.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为恢复后的 Record，deletedAt 为 null。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "101",
    "type": "DIARY",
    "title": "今天的学习记录",
    "content": "今天确定了毕设方向，明天下午三点到四点和导师讨论。",
    "recordDate": "2026-09-24",
    "tags": [
      {
        "id": "11",
        "name": "毕设"
      }
    ],
    "version": 3,
    "deletedAt": null,
    "createdAt": "2026-09-24T20:00:00+08:00",
    "updatedAt": "2026-09-24T21:00:00+08:00"
  },
  "requestId": "req-demo-001"
}
```

#### 2.6.4 备注说明

未在回收站返回 RECORD_NOT_IN_TRASH（409）；清除 deletedAt 并递增 version，不改变已有日程。

### 2.7 永久删除记录

#### 2.7.1 基本信息

> 请求路径：/api/v1/records/{id}/permanent
>
> 请求方式：DELETE
>
> 接口描述：永久删除本人回收站中的记录。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 2.7.2 请求参数

参数格式：path + queryString，无请求体。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |
| version | integer | 是 | Query；当前版本 |

请求参数样例：

```http
/api/v1/records/101/permanent?version=2
```

#### 2.7.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 null。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": null,
  "requestId": "req-demo-001"
}
```

#### 2.7.4 备注说明

仅允许删除回收站内容，否则 RECORD_NOT_IN_TRASH（409）。前端必须明确提示不可恢复。事务清理正文与标签关联、置空日程 sourceRecordId；日程独立保留，来源变化时递增其 version。已永久删除再次请求返回 404。

## 3. 标签管理

### 3.1 标签分页查询

#### 3.1.1 基本信息

> 请求路径：/api/v1/tags
>
> 请求方式：GET
>
> 接口描述：查询本人标签，供记录筛选与编辑使用。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 3.1.2 请求参数

参数格式：queryString。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| page | integer | 否 | Query；默认 1，最小 1 |
| pageSize | integer | 否 | Query；默认 20，范围 1～100 |

请求参数样例：

```http
/api/v1/tags?page=1&pageSize=20
```

#### 3.1.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为分页对象，rows 为 Tag[]；按 id 升序。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": "11",
        "name": "毕设"
      }
    ],
    "page": 1,
    "pageSize": 20
  },
  "requestId": "req-demo-001"
}
```

### 3.2 新增标签

#### 3.2.1 基本信息

> 请求路径：/api/v1/tags
>
> 请求方式：POST
>
> 接口描述：创建本人标签。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 3.2.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 去首尾空白后 1～20 字符；同一用户内忽略英文字母大小写唯一 |

请求参数样例：

```json
{
  "name": "毕设"
}
```

#### 3.2.3 响应数据

HTTP 201，application/json；通用外层字段见 0.3。

data 为 Tag。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "11",
    "name": "毕设"
  },
  "requestId": "req-demo-001"
}
```

#### 3.2.4 备注说明

名称重复返回 TAG_NAME_EXISTS（409）。

### 3.3 修改标签

#### 3.3.1 基本信息

> 请求路径：/api/v1/tags/{id}
>
> 请求方式：PUT
>
> 接口描述：修改本人标签名称。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 3.3.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |
| name | string | 是 | 去首尾空白后 1～20 字符；同一用户内忽略英文字母大小写唯一 |

请求参数样例：

```json
{
  "name": "毕业设计"
}
```

#### 3.3.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 Tag。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "11",
    "name": "毕业设计"
  },
  "requestId": "req-demo-001"
}
```

#### 3.3.4 备注说明

修改名称会在关联记录后续查询时显示；不修改记录正文。不设 version，首版并发修改采用最后成功写入值。

### 3.4 删除标签

#### 3.4.1 基本信息

> 请求路径：/api/v1/tags/{id}
>
> 请求方式：DELETE
>
> 接口描述：删除标签并解除所有本人记录与其关联。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 3.4.2 请求参数

参数格式：path，无请求体。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |

请求参数样例：

```http
/api/v1/tags/11
```

#### 3.4.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 null。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": null,
  "requestId": "req-demo-001"
}
```

#### 3.4.4 备注说明

不删除记录。关联集合发生变化的记录 version 递增，避免旧编辑表单重新带入已删除标签；操作在事务中完成。

## 4. 日程管理

### 4.1 日历范围查询

#### 4.1.1 基本信息

> 请求路径：/api/v1/events
>
> 请求方式：GET
>
> 接口描述：查询与指定北京时间日期范围相交的本人日程。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 4.1.2 请求参数

参数格式：queryString。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| from | string | 是 | Query；起始日期 YYYY-MM-DD，包含 |
| to | string | 是 | Query；结束日期 YYYY-MM-DD，不包含，跨度 1～62 天 |
| page | integer | 否 | Query；默认 1，最小 1 |
| pageSize | integer | 否 | Query；默认 20，范围 1～100 |

请求参数样例：

```http
/api/v1/events?from=2026-09-01&to=2026-10-01&page=1&pageSize=20
```

#### 4.1.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为分页对象，rows 为 Event[]。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": "201",
        "title": "和导师讨论毕设",
        "description": "讨论需求边界",
        "location": null,
        "timeKind": "TIMED",
        "startAt": "2026-09-25T15:00:00+08:00",
        "endAt": "2026-09-25T16:00:00+08:00",
        "startDate": null,
        "endDateExclusive": null,
        "sourceRecordId": "101",
        "sourceRecordState": "ACTIVE",
        "version": 1,
        "createdAt": "2026-09-24T20:05:00+08:00",
        "updatedAt": "2026-09-24T20:05:00+08:00"
      }
    ],
    "page": 1,
    "pageSize": 20
  },
  "requestId": "req-demo-001"
}
```

#### 4.1.4 备注说明

TIMED 按 from 当日 00:00+08:00 到 to 当日 00:00+08:00 的相交范围过滤，ALL_DAY 按日期范围相交过滤；跨天事件也返回。按有效开始时刻升序（全天视为当日零点）、id 升序。前端要继续分页直到取完，不能把第一页当作全部日程。

### 4.2 查询日程详情

#### 4.2.1 基本信息

> 请求路径：/api/v1/events/{id}
>
> 请求方式：GET
>
> 接口描述：查看本人日程及来源关联状态。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 4.2.2 请求参数

参数格式：path。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |

请求参数样例：

```http
/api/v1/events/201
```

#### 4.2.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 Event。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "201",
    "title": "和导师讨论毕设",
    "description": "讨论需求边界",
    "location": null,
    "timeKind": "TIMED",
    "startAt": "2026-09-25T15:00:00+08:00",
    "endAt": "2026-09-25T16:00:00+08:00",
    "startDate": null,
    "endDateExclusive": null,
    "sourceRecordId": "101",
    "sourceRecordState": "ACTIVE",
    "version": 1,
    "createdAt": "2026-09-24T20:05:00+08:00",
    "updatedAt": "2026-09-24T20:05:00+08:00"
  },
  "requestId": "req-demo-001"
}
```

### 4.3 创建日程／确认 AI 候选

#### 4.3.1 基本信息

> 请求路径：/api/v1/events
>
> 请求方式：POST
>
> 接口描述：手动创建日程，或将用户编辑确认后的候选保存为日程。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 4.3.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| title | string | 是 | 去除首尾空白后 1～100 字符 |
| description | string/null | 是 | 最长 2000 字符，无描述传 null |
| location | string/null | 是 | 最长 200 字符，无地点传 null |
| timeKind | string | 是 | TIMED 或 ALL_DAY |
| startAt、endAt | string/null | 是 | TIMED 必填带偏移的 ISO 时间；ALL_DAY 时都为 null |
| startDate、endDateExclusive | string/null | 是 | ALL_DAY 必填 YYYY-MM-DD；TIMED 时都为 null |
| acceptWarnings | boolean | 否 | 默认 false；用户已明确接受冲突／重复提示后才传 true |
| sourceRecordId | string/null | 是 | 手动创建为 null；AI 来源为当前用户正常记录 ID |
| sourceRecordVersion | integer/null | 是 | 来源为空时为 null；否则当前提取对应的记录版本 |
| creationRequestId | string | 是 | 客户端 UUID，同一次创建的重试保持不变 |

请求参数样例：

```json
{
  "title": "和导师讨论毕设",
  "description": "讨论需求边界",
  "location": null,
  "timeKind": "TIMED",
  "startAt": "2026-09-25T15:00:00+08:00",
  "endAt": "2026-09-25T16:00:00+08:00",
  "startDate": null,
  "endDateExclusive": null,
  "sourceRecordId": "101",
  "sourceRecordVersion": 1,
  "creationRequestId": "fdafaf97-6dc2-4f9e-997a-7c00223735cd",
  "acceptWarnings": false
}
```

#### 4.3.3 响应数据

HTTP 201，application/json；通用外层字段见 0.3。

data 包含保存的日程、提示与幂等标记。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| event | Event | 是 | 创建的日程；重放返回原日程当前状态 |
| warnings | Warning[] | 是 | 本次接受的提示，无则 []；重放时返回 []，不重新检测 |
| replayed | boolean | 是 | 首次 false；幂等重放 true，HTTP 200 |

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "event": {
      "id": "201",
      "title": "和导师讨论毕设",
      "description": "讨论需求边界",
      "location": null,
      "timeKind": "TIMED",
      "startAt": "2026-09-25T15:00:00+08:00",
      "endAt": "2026-09-25T16:00:00+08:00",
      "startDate": null,
      "endDateExclusive": null,
      "sourceRecordId": "101",
      "sourceRecordState": "ACTIVE",
      "version": 1,
      "createdAt": "2026-09-24T20:05:00+08:00",
      "updatedAt": "2026-09-24T20:05:00+08:00"
    },
    "warnings": [],
    "replayed": false
  },
  "requestId": "req-demo-001"
}
```

#### 4.3.4 备注说明

字段完整、来源归属与版本均正确后再检测提示。来源在回收站返回 RECORD_IN_TRASH；来源版本变化返回 SOURCE_RECORD_CHANGED（409）。明确时间与全天规则见 4.6；用户接受提示的请求才允许带 acceptWarnings=true。创建日程不会更新来源记录。

### 4.4 修改日程

#### 4.4.1 基本信息

> 请求路径：/api/v1/events/{id}
>
> 请求方式：PUT
>
> 接口描述：完整更新日程的可编辑内容。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 4.4.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |
| title | string | 是 | 去除首尾空白后 1～100 字符 |
| description | string/null | 是 | 最长 2000 字符，无描述传 null |
| location | string/null | 是 | 最长 200 字符，无地点传 null |
| timeKind | string | 是 | TIMED 或 ALL_DAY |
| startAt、endAt | string/null | 是 | TIMED 必填带偏移的 ISO 时间；ALL_DAY 时都为 null |
| startDate、endDateExclusive | string/null | 是 | ALL_DAY 必填 YYYY-MM-DD；TIMED 时都为 null |
| acceptWarnings | boolean | 否 | 默认 false；用户已明确接受冲突／重复提示后才传 true |
| version | integer | 是 | 当前读取到的版本号，最小 1；不匹配返回 409 |

请求参数样例：

```json
{
  "title": "和导师讨论毕设",
  "description": "讨论需求边界",
  "location": null,
  "timeKind": "TIMED",
  "startAt": "2026-09-25T15:00:00+08:00",
  "endAt": "2026-09-25T16:00:00+08:00",
  "startDate": null,
  "endDateExclusive": null,
  "acceptWarnings": false,
  "version": 1
}
```

#### 4.4.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data.event 为 Event；data.warnings 为已接受的 Warning[]。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "event": {
      "id": "201",
      "title": "和导师讨论毕设",
      "description": "讨论需求边界",
      "location": null,
      "timeKind": "TIMED",
      "startAt": "2026-09-25T15:00:00+08:00",
      "endAt": "2026-09-25T16:00:00+08:00",
      "startDate": null,
      "endDateExclusive": null,
      "sourceRecordId": "101",
      "sourceRecordState": "ACTIVE",
      "version": 2,
      "createdAt": "2026-09-24T20:05:00+08:00",
      "updatedAt": "2026-09-24T20:15:00+08:00"
    },
    "warnings": []
  },
  "requestId": "req-demo-001"
}
```

#### 4.4.4 备注说明

sourceRecordId 不可通过此接口修改，不提交 sourceRecordVersion 或 creationRequestId。检测冲突／重复时排除自身；只修改日程，不修改原文。旧版本返回 VERSION_CONFLICT。

### 4.5 删除日程

#### 4.5.1 基本信息

> 请求路径：/api/v1/events/{id}
>
> 请求方式：DELETE
>
> 接口描述：删除本人日程。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 4.5.2 请求参数

参数格式：path + queryString，无请求体。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |
| version | integer | 是 | Query；日程版本 |

请求参数样例：

```http
/api/v1/events/201?version=1
```

#### 4.5.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为 null。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": null,
  "requestId": "req-demo-001"
}
```

#### 4.5.4 备注说明

日程无回收站，前端先确认删除。保留不含正文的最小创建凭据，重放旧创建请求返回 ORIGINAL_EVENT_DELETED（409）；不重建。

### 4.6 时间与提示规则

- TIMED：startAt、endAt 非空，结束晚于开始；日期字段必须为 null。
- ALL_DAY：startDate、endDateExclusive 非空且右端较大，时刻字段必须为 null。单日 9 月 25 日表示为 2026-09-25 至 2026-09-26。
- 定时时间冲突按半开区间比较，相邻但不重叠的事项不冲突；全天事项不参与时间冲突判定。
- 疑似重复：本人日程的规范化标题、timeKind 与相应时间范围相同。标题规范化为去首尾空白、连续空白折叠为一个空格；不同来源仍可以判为重复。
- 默认 acceptWarnings=false。发现冲突或疑似重复返回 HTTP 409、EVENT_CONFIRMATION_REQUIRED，不写入。用户查看提示后以相同业务字段及 acceptWarnings=true 再提交；若编辑内容则创建操作更换 creationRequestId，修改操作仍携带最新 version。
- acceptWarnings=true 表示接受本次重新检测到的所有提示；提示不是排他预约，不保证同时保存时不存在新重叠。

提示响应示例：

```json
{
  "code": "EVENT_CONFIRMATION_REQUIRED",
  "message": "发现冲突或疑似重复，请确认是否仍要保存",
  "data": {
    "warnings": [
      {
        "type": "TIME_CONFLICT",
        "message": "与已有日程时间重叠",
        "eventIds": [
          "202"
        ],
        "total": 1
      }
    ]
  },
  "requestId": "req-demo-003"
}
```

全天手动创建请求示例（POST /api/v1/events）：

```json
{
  "title": "整理毕设材料",
  "description": null,
  "location": null,
  "timeKind": "ALL_DAY",
  "startAt": null,
  "endAt": null,
  "startDate": "2026-09-25",
  "endDateExclusive": "2026-09-26",
  "sourceRecordId": null,
  "sourceRecordVersion": null,
  "creationRequestId": "e7ff100f-6e01-440d-b979-964f1fb57b90",
  "acceptWarnings": false
}
```

## 5. AI 辅助功能

### 5.1 根据要点整理成文

#### 5.1.1 基本信息

> 请求路径：/api/v1/ai/compose
>
> 请求方式：POST
>
> 接口描述：根据用户本次提交的零散要点生成文字建议。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 5.1.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| points | string | 是 | 非空文本，最长 5000 字符 |

请求参数样例：

```json
{
  "points": "今天确定了毕设方向；学会了在 IDEA 启动 Spring Boot；明天继续学习接口。"
}
```

#### 5.1.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为写作建议。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| suggestedText | string | 是 | 生成的 Markdown 建议，不保存到记录 |
| promptVersion | string | 是 | 本次提示词版本，用于效果评估 |

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "suggestedText": "今天，我确定了毕设方向，并学会了在 IDEA 中启动 Spring Boot。明天计划继续学习接口开发。",
    "promptVersion": "compose-v1"
  },
  "requestId": "req-demo-001"
}
```

#### 5.1.4 备注说明

不自动加载任何历史日记；不自动保存。用户采用后将文本放回草稿，通过记录保存接口提交。编辑期间原文改变时先重新比较，不直接替换。

### 5.2 润色选中文字

#### 5.2.1 基本信息

> 请求路径：/api/v1/ai/polish
>
> 请求方式：POST
>
> 接口描述：仅润色用户选中的文字。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 5.2.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| text | string | 是 | 选中原文，非空且最长 5000 字符 |

请求参数样例：

```json
{
  "text": "今天把项目启动了，有点开心，明天接着学。"
}
```

#### 5.2.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为写作建议。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| suggestedText | string | 是 | 生成的 Markdown 建议，不保存到记录 |
| promptVersion | string | 是 | 本次提示词版本，用于效果评估 |

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "suggestedText": "今天成功启动了项目，心里很开心。明天继续学习。",
    "promptVersion": "polish-v1"
  },
  "requestId": "req-demo-001"
}
```

#### 5.2.4 备注说明

只发送 text，不附带整篇记录或历史上下文。结果待用户预览采用，不改写原文。

### 5.3 从记录提取候选日程

#### 5.3.1 基本信息

> 请求路径：/api/v1/records/{id}/extract-events
>
> 请求方式：POST
>
> 接口描述：从当前用户已保存的正常记录提取候选安排。
>
> 登录要求：需要登录；写请求同时要求 CSRF
>
> 实现状态：未实现（设计稿）

#### 5.3.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| id | string | 是 | 路径参数，资源 ID；仅操作当前用户的资源 |
| version | integer | 是 | 当前读取到的版本号，最小 1；不匹配返回 409 |

请求参数样例：

```json
{
  "version": 1
}
```

#### 5.3.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 为本次提取结果。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| sourceRecordId | string | 是 | 来源记录 ID |
| sourceRecordVersion | integer | 是 | 本次所读记录版本 |
| referenceDate | string | 是 | 日记采用记录日期；笔记采用请求时北京时间日期 |
| timezone | string | 是 | 固定 Asia/Shanghai |
| candidates | Candidate[] | 是 | 候选列表；无日程返回 [] |
| promptVersion | string | 是 | 提示词版本 |

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "sourceRecordId": "101",
    "sourceRecordVersion": 1,
    "referenceDate": "2026-09-24",
    "timezone": "Asia/Shanghai",
    "candidates": [
      {
        "candidateKey": "c1",
        "title": "和导师讨论毕设",
        "description": null,
        "location": null,
        "timeKind": "TIMED",
        "startAt": "2026-09-25T15:00:00+08:00",
        "endAt": "2026-09-25T16:00:00+08:00",
        "startDate": null,
        "endDateExclusive": null,
        "evidenceText": "明天下午三点到四点和导师讨论",
        "missingFields": [],
        "uncertaintyReasons": []
      }
    ],
    "promptVersion": "extract-events-v1"
  },
  "requestId": "req-demo-001"
}
```

#### 5.3.4 备注说明

先保存再调用；服务器读取正文，不接受前端另传正文或基准日期。检查归属、回收站状态和 version 后再调用模型；不自动发起重复调用。候选最多 20 项，过多返回 AI_RESULT_TOO_LARGE（422），不静默截断。用户逐项补充确认后调用 4.3；candidateKey 不提交到日程创建接口，不作为权限凭据。

### 5.4 不确定与失败结果

“可能明天下午见导师，时间待定”的候选示例：

```json
{
  "candidateKey": "c1",
  "title": "见导师",
  "description": null,
  "location": null,
  "timeKind": "TIMED",
  "startAt": null,
  "endAt": null,
  "startDate": null,
  "endDateExclusive": null,
  "evidenceText": "可能明天下午见导师，时间待定",
  "missingFields": [
    "startAt",
    "endAt"
  ],
  "uncertaintyReasons": [
    "安排尚未确定",
    "未提供明确起止时间"
  ]
}
```

没有日程属于正常结果，HTTP 200，candidates=[]。缺少时间不能自动转为全天事项；只有明确的全天安排才使用 ALL_DAY。

| 情况 | HTTP | code | 客户端行为 |
| --- | --- | --- | --- |
| 未配置 AI 服务 | 503 | AI_UNAVAILABLE | 保留原文，使用普通编辑功能 |
| 请求超过长度上限 | 422 | VALIDATION_ERROR | 提示缩短输入 |
| 候选超过上限 | 422 | AI_RESULT_TOO_LARGE | 提示拆分记录后重试 |
| 用户／服务并发限流 | 429 | RATE_LIMITED | 稍后由用户手动重试 |
| 上游调用失败 | 502 | AI_UPSTREAM_ERROR | 保留原文，提示失败 |
| 模型返回结构无法校验 | 502 | AI_INVALID_RESPONSE | 不接受部分错误结果 |
| 模型拒绝处理 | 422 | AI_REFUSED | 提示用户调整输入 |
| 超时 | 504 | AI_TIMEOUT | 保留原文；不自动重试 |

```json
{
  "code": "AI_TIMEOUT",
  "message": "AI 处理超时，原文未修改，请稍后重试",
  "data": null,
  "requestId": "req-demo-004"
}
```

模型响应、提示词和用户正文不得写入普通日志。失败 message 不透传供应商原始响应或凭据。AI 结果不是事实保证，用户确认和业务校验始终保留。

## 6. Java → Python 内部接口

以下接口不供 Vue 调用，部署于私有网络。要求服务身份请求头 `X-Internal-Token`（通过部署密钥配置，不使用用户 Cookie）、`X-Request-Id`（Java 生成并传递的链路 ID）。内部认证失败统一 401／INTERNAL_UNAUTHORIZED。内部响应沿用 0.3 外层；业务 code 由 Java 映射为第 5.4 节的公共错误，Python 未授权等内部故障不得映射为用户未登录。

### 6.1 内部要点整理

#### 6.1.1 基本信息

> 请求路径：/internal/v1/compose
>
> 请求方式：POST
>
> 接口描述：Java 委托 Python 整理本次要点。
>
> 登录要求：内部服务凭据，不使用用户 Session／CSRF
>
> 实现状态：未实现（设计稿）

#### 6.1.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| contractVersion | string | 是 | 固定 1；不支持的版本返回 400／UNSUPPORTED_CONTRACT_VERSION |
| points | string | 是 | 规则同 5.1 |

请求参数样例：

```json
{
  "contractVersion": "1",
  "points": "今天确定毕设方向，明天学习接口。"
}
```

#### 6.1.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 与对应浏览器 AI 接口的写作建议一致。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| suggestedText | string | 是 | 生成的 Markdown 建议，不保存到记录 |
| promptVersion | string | 是 | 本次提示词版本，用于效果评估 |

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "suggestedText": "今天确定了毕设方向，明天计划学习接口。",
    "promptVersion": "compose-v1"
  },
  "requestId": "req-demo-001"
}
```

#### 6.1.4 备注说明

只处理本次输入，不访问业务数据库；requestId 回传 X-Request-Id。写作建议最长 20000 字符；响应超限视为 AI_INVALID_RESPONSE，不截断返回。

### 6.2 内部文本润色

#### 6.2.1 基本信息

> 请求路径：/internal/v1/polish
>
> 请求方式：POST
>
> 接口描述：Java 委托 Python 润色选中文字。
>
> 登录要求：内部服务凭据，不使用用户 Session／CSRF
>
> 实现状态：未实现（设计稿）

#### 6.2.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| contractVersion | string | 是 | 固定 1；不支持的版本返回 400／UNSUPPORTED_CONTRACT_VERSION |
| text | string | 是 | 规则同 5.2 |

请求参数样例：

```json
{
  "contractVersion": "1",
  "text": "今天把项目启动了。"
}
```

#### 6.2.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data 与对应浏览器 AI 接口的写作建议一致。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| suggestedText | string | 是 | 生成的 Markdown 建议，不保存到记录 |
| promptVersion | string | 是 | 本次提示词版本，用于效果评估 |

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "suggestedText": "今天成功启动了项目。",
    "promptVersion": "polish-v1"
  },
  "requestId": "req-demo-001"
}
```

#### 6.2.4 备注说明

只处理本次输入，不访问业务数据库；requestId 回传 X-Request-Id。写作建议最长 20000 字符；响应超限视为 AI_INVALID_RESPONSE，不截断返回。

### 6.3 内部日程提取

#### 6.3.1 基本信息

> 请求路径：/internal/v1/extract-events
>
> 请求方式：POST
>
> 接口描述：根据正文与可信日期上下文输出结构化候选。
>
> 登录要求：内部服务凭据，不使用用户 Session／CSRF
>
> 实现状态：未实现（设计稿）

#### 6.3.2 请求参数

参数格式：application/json。

| 参数名 | 类型 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| contractVersion | string | 是 | 固定 1；不支持的版本返回 400／UNSUPPORTED_CONTRACT_VERSION |
| text | string | 是 | 当前记录正文，非空，最长 20000 字符 |
| referenceDate | string | 是 | Java 确定的基准日期 YYYY-MM-DD |
| timezone | string | 是 | 固定 Asia/Shanghai |

请求参数样例：

```json
{
  "contractVersion": "1",
  "text": "今天确定了毕设方向，明天下午三点到四点和导师讨论。",
  "referenceDate": "2026-09-24",
  "timezone": "Asia/Shanghai"
}
```

#### 6.3.3 响应数据

HTTP 200，application/json；通用外层字段见 0.3。

data.candidates 为 Candidate[]（最多 20 项），data.promptVersion 为 string。

响应数据样例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "candidates": [
      {
        "candidateKey": "c1",
        "title": "和导师讨论毕设",
        "description": null,
        "location": null,
        "timeKind": "TIMED",
        "startAt": "2026-09-25T15:00:00+08:00",
        "endAt": "2026-09-25T16:00:00+08:00",
        "startDate": null,
        "endDateExclusive": null,
        "evidenceText": "明天下午三点到四点和导师讨论",
        "missingFields": [],
        "uncertaintyReasons": []
      }
    ],
    "promptVersion": "extract-events-v1"
  },
  "requestId": "req-demo-001"
}
```

#### 6.3.4 备注说明

不接收 userId 或数据库连接信息。Java 自行补入 sourceRecordId、sourceRecordVersion、referenceDate 与 timezone 形成 5.3 响应，不相信模型返回的身份或来源字段。Pydantic 与 Java 均验证结构及时间字段；全部候选通过校验才返回成功。空正文在 Java 直接返回 candidates=[]，不调用模型；同样的内部空正文请求返回 422。

## 7. 错误码汇总

| HTTP | code | 说明 |
| --- | --- | --- |
| 400 | BAD_REQUEST | JSON、类型、枚举、日期格式或未知字段错误 |
| 401 | UNAUTHENTICATED | 未登录或会话失效 |
| 401 | INVALID_CREDENTIALS | 登录账号或密码错误 |
| 403 | CSRF_INVALID | 写请求的 CSRF token 缺失或无效 |
| 404 | RESOURCE_NOT_FOUND | 资源不存在或不属于当前用户 |
| 409 | USERNAME_EXISTS | 用户名已存在 |
| 409 | TAG_NAME_EXISTS | 标签名已存在 |
| 409 | VERSION_CONFLICT | 记录／日程版本过期 |
| 409 | SOURCE_RECORD_CHANGED | 提取所依据的来源版本已改变 |
| 409 | RECORD_IN_TRASH | 操作不允许用于回收站记录 |
| 409 | RECORD_NOT_IN_TRASH | 恢复／永久删除要求记录在回收站 |
| 409 | EVENT_CONFIRMATION_REQUIRED | 检测到日程冲突／疑似重复，等待用户接受 |
| 409 | IDEMPOTENCY_CONFLICT | 创建 ID 已用于不同业务内容 |
| 409 | ORIGINAL_EVENT_DELETED | 旧创建请求对应的日程已经删除 |
| 422 | VALIDATION_ERROR | 字段长度、必填、时间先后等业务校验失败 |
| 422 | OLD_PASSWORD_INVALID | 修改密码时旧密码不正确 |
| 422 | AI_REFUSED / AI_RESULT_TOO_LARGE | AI 拒绝／候选过多，详情见 5.4 |
| 429 | RATE_LIMITED | 请求过于频繁，可提供 Retry-After 秒数 |
| 502 | AI_UPSTREAM_ERROR / AI_INVALID_RESPONSE | 模型调用或输出校验失败 |
| 503 | AI_UNAVAILABLE | AI 服务未配置或不可用 |
| 504 | AI_TIMEOUT | AI 处理超时 |
| 500 | INTERNAL_ERROR | 未预期的服务器错误，不返回堆栈 |

内部专用 code：INTERNAL_UNAUTHORIZED（401）、UNSUPPORTED_CONTRACT_VERSION（400）。Python 内部框架默认校验错误须适配统一响应结构；不能直接透传 FastAPI 的 detail 格式给浏览器。

### 7.1 关键处理顺序

- 公共受保护接口：会话 → CSRF（写操作）→ 请求格式 → 资源归属 → 版本／状态 → 业务校验 → 写入。
- 创建日程：会话与 CSRF → 请求格式 → 本人创建凭据重放 → 来源归属与版本 → 时间校验 → 冲突／重复提示 → 事务创建。
- AI 提取：身份与记录权限在外部模型调用之前验证；调用过程不持有数据库事务。返回前再次检查记录仍可访问且版本未变，否则 SOURCE_RECORD_CHANGED 或 RECORD_IN_TRASH／404，不交付已过期建议。
- 任何失败均不进行部分业务写入；接受提示后仍需在保存前检查 version，避免提示期间被其他页面修改。

## 8. 建议联调顺序与验收清单

1. 获取 CSRF → 注册 → 登录 → 刷新 CSRF → 查询当前用户。
2. 创建标签 → 创建日记 → 列表搜索 → 详情 → 带 version 修改。
3. 移入回收站 → 阅读详情 → 恢复；另用测试记录验证永久删除。
4. 手动创建日程 → 查询日历 → 修改 → 冲突确认 → 重复请求验证。
5. 固定样例模拟 AI → 润色预览 → 提取候选 → 补齐字段 → 确认创建。
6. 接入真实模型后，再验证模型效果、拒绝、无日程和超时情况。

必须验证的反例：

- 用户 B 替换用户 A 的记录／标签／日程 ID，查询与写入均不得泄露或修改内容。
- 未登录、旧会话、无 CSRF 写请求均按约定失败；不在响应暴露密码。
- 旧 version 不覆盖新内容；来源删除或修改后，旧候选不能直接创建。
- 并发提交同一 creationRequestId 只生成一项日程；删除后的请求重放不复活日程。
- 相邻日程不冲突；跨天日程能被日期范围查询命中；日历取完所有分页。
- 只有“明天下午”等模糊时间不会被补造成明确时间；无日程返回空集合。
- AI 不可用时仍可保存记录和手动日程，原文不被部分结果覆盖。

本轮仅核对文档结构、示例 JSON 和接口覆盖；以上联调与运行验收尚未执行。首个业务接口实现后应更新对应状态，文档与源码差异必须明确记录。
