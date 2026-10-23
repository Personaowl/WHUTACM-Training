# 接口参考

[文档索引](README.md) · [功能与权限](features.md) · [数据库](db-design.md)

按 2026-10-23 的当前源码核对。本文是已有接口的对接说明，不是待实现设计；精确字段以 [Controller](../backend/src/main/java/com/whut/training/controller/) 与 [DTO](../backend/src/main/java/com/whut/training/domain/dto/) 为准。生产环境在网站同源下请求 `/api`，本地开发由 Vite 代理到 8080。

## 1. 响应、认证与错误

除下面注明的例外，成功响应为：

```json
{"code":200,"message":"success","data":{}}
```

- 普通业务接口由全局异常处理返回对应 HTTP 状态及 `{code,message,data}`：400 参数/业务条件不符，401 登录失效，403 权限不足，404 不存在，409 冲突，500 内部错误，503 外部服务/队列暂不可用。不能只检查 HTTP 200，也不能假设所有错误都包在 HTTP 200 中。
- **首页成功响应直接返回对象，没有外层 code/data；Excel 成功响应为二进制文件。** Nginx 拦截或限流响应也不保证是 JSON。
- 除健康检查、注册和登录外，通常要求 `Authorization: Bearer <accessToken>`。刷新和注销不经过 Access Token 拦截器，但必须提供有效的 `X-Refresh-Token`。
- 管理员指 `ADMIN` 或 `SUPER_ADMIN`；超管指 `SUPER_ADMIN`。角色与 `REGULAR / ACTIVE_TEAM` 成员身份独立。
- 默认访问令牌 2 小时、整个登录会话最长 6 小时；刷新返回新双令牌并消费旧刷新令牌，不延长首次登录的绝对期限。生产可配置 TTL。
- `/api/dev` 调试控制器是例外：部分失败直接返回业务失败对象，HTTP 状态不一定同步。仅用于隔离开发环境，不用它模拟生产错误协议。

### 无需 Access Token 的接口

| 方法与路径 | 请求 / 返回 |
| --- | --- |
| GET /api/health | 数据库探测；成功 data 包含 time、status、database |
| POST /api/users/register | 见下方注册字段；返回 User，不自动签发令牌 |
| POST /api/auth/login | JSON：username、password；返回 LoginResponse |
| POST /api/auth/refresh | 必填 X-Refresh-Token；返回 accessToken、refreshToken |
| POST /api/auth/logout | 必填 X-Refresh-Token，可带 Authorization；注销会话，返回空 data |

注册必填 `username`（最多 50 字符）、`displayName`（最多 50）、`password`（8～64）。选填 `email`（有效邮箱、最多 255）、`realName`（最多 100）、`college`、`className`（各最多 150）。登录名不是 CF Handle，注册不访问 CF。

LoginResponse 包含 `id, username, email, role, uid, codeforcesRating, maxRating, online, lastOnlineTimeSeconds, avatarUrl, codeforcesHandle, pendingCodeforcesHandle, codeforcesBindingStartedAtSeconds, displayName, accessToken, refreshToken`。不要假定登录响应还包含全部个人资料；完整资料另取用户接口。

## 2. 用户、头像与绑定

| 方法与路径 | 权限 | 参数 / 说明 |
| --- | --- | --- |
| GET /api/users | 管理员 | 按 ID 排序的用户列表；没有后端关键词分页搜索参数，管理页在前端筛选 |
| GET /api/users/{id} | 本人或管理员 | 完整 User 资料，不输出密码或 AtCoder 验证码 |
| PATCH /api/users/{id} | 本人或超管 | UserUpdateRequest；普通管理员不能修改他人账号资料 |
| GET /api/users/{id}/public-profile | 已登录 | PublicUserProfile，给其他成员查看的安全资料 |
| GET /api/users/{id}/codeforces-overview | 已登录 | CF 统计快照；缓存/异步刷新，不保证请求时即时抓取完毕 |
| POST /api/users/{id}/codeforces-overview/refresh | 本人或管理员 | 请求刷新 CF 快照 |
| GET /api/users/{id}/daily-heatmap | 本人或管理员 | days 默认 180，最大 365；返回 date、score、colorLevel 列表，不是 CF 全量做题数据 |
| POST /api/users/{id}/codeforces-binding/start | 仅本人 | JSON：handle；返回验证题目、开始和截止时间等信息 |
| POST /api/users/{id}/codeforces-binding/finish | 仅本人 | 无请求体，验证后返回 User |
| POST /api/users/{id}/atcoder-binding/start | 仅本人 | JSON：handle；返回临时 Affiliation 验证码等信息 |
| POST /api/users/{id}/atcoder-binding/finish | 仅本人 | 无请求体，验证后返回 User |

资料更新字段：`username, email, password, displayName, realName, college, className, avatar, bio, showProblemTags`。不修改密码时应**省略 password**，不要发送空字符串（DTO 要求非空值长度 8～64）。头像传 `avatar` 而不是 `avatarUrl`，只接受 PNG/JPEG/WebP 的 Base64 data image，字符串最多 700000 字符，不接受外部 URL。前端负责裁剪压缩。

公开资料只包含 `id, displayName, avatarUrl, bio, memberType, codeforcesHandle, atcoderHandle, codeforcesRating, maxRating, totalPoints`，不包含登录账号、邮箱、姓名、学院、班级或审核说明。

CF 绑定窗口为 120 秒，要求验证窗口内向 CF 1A 提交 Compilation Error；AtCoder 窗口为 600 秒，临时修改 Affiliation，成功后可恢复。绑定接口不会代替用户向外部平台提交或修改设置。

### 洛谷与牛客所有权验证

前缀为 `/api/users/{userId}/platform-bindings/{platform}`，platform 仅 luogu / nowcoder。三个接口均仅限本人，管理员也不能查看/代操作他人验证码。

| 方法与路径 | 请求 / 返回 |
| --- | --- |
| GET /api/users/{userId}/platform-bindings/{platform} | 当前已绑定 UID 及本人仍有效的验证挑战 |
| POST /api/users/{userId}/platform-bindings/{platform}/start | JSON：uid，1～18 位正整数文本；生成 10 分钟挑战，不直接绑定 |
| POST /api/users/{userId}/platform-bindings/{platform}/finish | 无业务请求体；读取对应账号简介验证随机码，成功后消费挑战并保存绑定 |

返回字段：platform、accountId、verifiedAtSeconds、pendingAccountId、verificationToken、expiresAtSeconds、profileUrl。仅 accountId 非空代表已绑定；待验证不算成功。开始间隔 10 秒、验证间隔 5 秒，过密返回 429；未匹配/过期返回 400，重复账号或挑战已更新返回 409，外部页面无法安全读取返回 503。失败不会覆盖原已验证账号。本轮不增加两平台的训练统计、导出或公开资料字段。

## 3. 每日题与自主练习

以下均要求登录，只操作当前用户记录。

| 方法与路径 | 参数 / 返回 |
| --- | --- |
| GET /api/daily-problem/today | data 为 problems、checkedIn、score；problems 为当前有效题位，checkedIn 表示任意题位完成，score 为当天最高分 |
| POST /api/daily-problem/check-in | JSON：正整数 submissionId；**返回异步任务**而非直接返回最终校验结果 |
| GET /api/daily-problem/check-in/{jobId} | 查询自己的任务；别人任务 403，不存在/已清理任务 404 |
| GET /api/daily-problem/history | days 默认 0，非正数取全部现存范围（实现下限 2020-01-01）；不展开重抽旧题为额外题位 |
| POST /api/practice/draw | 可选 JSON：minRating、maxRating、tags；返回 drawId、problem |
| GET /api/practice/tags | 可选标签列表 |
| GET /api/practice/history | limit 默认 20；个人抽题记录 |
| DELETE /api/practice/{drawId} | 删除自己的抽题记录 |

任务字段为 `jobId, status, message, errorCode, result`。状态为 `PENDING / RUNNING / SUCCEEDED / FAILED`；失败任务通常仍由任务查询接口成功返回，必须检查 status/errorCode。成功 result 为 `type, accepted, submissionId, verdict, score`，其中 score 是**当前当天总分**，不是此次新增积分。

校验要求绑定账号最近 30 条提交中存在该 ID、匹配目标日期当前有效题、verdict 为 OK；不扩大查询或回退全量。当前实现**没有额外比较提交创建时间与当天零点/题目生成时间**，也没有历史补卡日期请求参数；异步任务在创建时固定目标日期。计分和重抽规则见 [功能说明](features.md)。

任务存在于单进程内存中；进行中同一用户/日期/提交请求合并，成功结果 10 分钟内复用，失败可重试。已结束任务保留约 1 小时，定时清理；重启后任务 ID 失效，数据库已完成的计分不受影响。

练习页面提供 800～3500、步长 100 的选项，这是**前端选项范围**，不是后端严格百位枚举校验。省略上下限时后端默认 1200～2000；tags 最多 5 个、同时匹配。练习不校验提交、不计积分，没有 practice check-in 接口。

## 4. 题单

前缀 `/api/problem-lists`，均需登录。可读自己的题单及所有共享题单。任意登录成员可创建共享题单并向共享题单加题；题单名称/简介/删除仍仅限创建者，题目由添加者、题单创建者或管理员（仅共享题单）维护。

| 方法与路径 | 请求 / 返回 |
| --- | --- |
| GET /api/problem-lists | 可见题单摘要列表 |
| GET /api/problem-lists/{id} | 题单摘要与条目 |
| POST /api/problem-lists | name（必填，最多 80）、description（最多 500）、shared |
| PATCH /api/problem-lists/{id} | 同创建字段；shared=true 对所有登录成员开放 |
| DELETE /api/problem-lists/{id} | 删除题单及条目 |
| POST /api/problem-lists/{id}/items | 添加条目 |
| PATCH /api/problem-lists/{id}/items/{itemId} | 更新条目 |
| DELETE /api/problem-lists/{id}/items/{itemId} | 删除条目 |

条目字段：`title, link, note, problemKey, rating, tags`。link 必填有效 HTTP/HTTPS 地址；本地题库能识别 CF 题目时可补充元数据，否则须提供 title。条目响应新增 addedBy，贡献者 ID 由后端当前身份写入，客户端不能指定。已有他人贡献时共享改私有返回 409。写入锁定题单行；同一题单重复链接返回 409。题单仅一级分类，没有嵌套文件夹。

## 5. 首页、排行榜与比赛

| 方法与路径 | 权限 | 参数 / 返回 |
| --- | --- | --- |
| GET /api/home | 已登录 | top 默认 10，服务限制 1～50；直接返回 HomeOverviewResponse |
| GET /api/leaderboard | 已登录 | page 默认 1、limit 默认 10（最大 100）；data：items、total、page、limit |
| GET /api/contests/upcoming | 已登录 | CF / AtCoder / 牛客 / 洛谷的比赛聚合列表 |

首页字段：`totalUsers, topUsers, todayProblem, dailySubmissionSummary, problemPoolInitializing, currentUserId, currentUserPoints`。历史命名 totalUsers 表示**过去 7 天有每日题记录的活跃用户数**，不是全部注册数。dailySubmissionSummary 包含 todaySubmissions、todayCheckedInUsers。currentUserPoints 为当前认证用户的数据库积分，不混入共享概览缓存。

排行榜只提供总积分排名，排除 SUPER_ADMIN。兼容参数 type 默认 total，当前不切换其他榜单，不能据此推断存在周榜/月榜。

## 6. 旧推题接口下线

原 `/api/push`、`/api/admin/push` 及其子路径不再注册，已登录访问旧路径返回 404；未登录请求可能先被认证拦截。推荐题目改用上文共享题单接口，无审核和每日发布环节。

## 7. 管理、训练看板与导出

| 方法与路径 | 权限 / 参数 |
| --- | --- |
| POST /api/admin/users | 管理员；username、email、password、role 必填；普通 ADMIN 只能创建 USER，超管可创建高权限账号 |
| PUT /api/admin/users/{id}/role | 超管；JSON：role；写审计，不能降级最后一位超管 |
| PUT /api/admin/users/{id}/member-type | 管理员；JSON：memberType=REGULAR 或 ACTIVE_TEAM |
| POST /api/admin/users/{id}/reset-password | 超管；其他非超管账号重置为 123456 并注销其会话 |
| GET /api/roles | 管理员；角色名称/代码/描述列表 |
| POST /api/admin/daily-problem/redraw | 管理员；query：slot=easy/hard，date 可选默认今天，confirm 默认 false |
| POST /api/admin/daily-problem/regenerate | 管理员；兼容旧单题 daily_problem 的重新生成，返回单个 ProblemView，**不是同时重抽 Easy/Hard** |
| GET /api/admin/training-dashboard | 管理员；现役成员每日题等训练统计 |
| POST /api/admin/training-dashboard/export | 管理员；返回 .xlsx 文件，见下方选项 |
| GET /api/admin/atcoder-abc | 管理员；可选 contestId |
| POST /api/admin/atcoder-abc/refresh | 管理员；可选 contestId，刷新跟踪数据 |
| PATCH /api/admin/atcoder-abc/setting | 管理员；JSON：minimumAcCount（0～8）、graceHours（1～168），可选 query contestId |
| PATCH /api/admin/atcoder-abc/{contestId}/members/{userId}/exemption | 管理员；JSON：exempted、reason（最多 500） |

redraw 的 confirm 当前不改变补记行为，不代表确认后重算历史积分。兼容 regenerate 仍有界面入口；若要更换双题位，应使用对应 redraw，而不是把旧单题接口当作双题位重建。

导出 JSON 字段为 `range, includeDaily, includeCodeforcesContests, includeAtCoderContests`，至少选择一种。range 为 WEEK / MONTH / ALL，空或未知值按 WEEK；WEEK 指含今天最近 7 天、MONTH 指含今天最近 30 天，**不是自然周/月**。CF Rated 数据来自同步记录，ABC 来自已跟踪场次；导出不包含自主练习、私人情况说明或人工周状态。成功响应 Content-Type 为 XLSX，文件名见 Content-Disposition。

## 8. 周训练与情况说明

| 方法与路径 | 权限 / 请求 |
| --- | --- |
| GET /api/weekly-training/me | 已登录；可选 query week=YYYY-MM-DD，仅本人 |
| POST /api/weekly-training/me/explanation | 该周名册成员；必填 query week；JSON：content（1～2000）、version（非负） |
| GET /api/admin/weekly-training | 管理员；可选 query week |
| POST /api/admin/weekly-training/{userId}/review | 管理员；必填 query week；JSON：action、status、note、version |

week 归一到上海时区该周周一，不能选未来周。返回 WeeklyTrainingReport：weekStart、weekEnd（周日）、today、members。成员包含每日完成、比赛结果、automaticStatus、manualStatus、effectiveStatus、explanation、version 和最近 20 条 history。

- 自动状态：COMPLETED / IN_PROGRESS / PENDING / INCOMPLETE；manualStatus 为 AUTO / COMPLETED / EXEMPT / INCOMPLETE。
- 说明审核状态：NONE / PENDING / APPROVED / REJECTED。
- action 为 APPROVE / REJECT / ADJUST；note 必填 1～2000 字。APPROVE、ADJUST 需提供有效 status，AUTO 表示恢复自动判定。
- APPROVE / REJECT 只处理待审核说明；有待审核说明时不能直接 ADJUST。过期 version 或重复处理返回 409，先刷新再操作。
- 本人查询无对应名册时 members 可为空；管理员查全体。权限与统计细则见功能文档，不公开他人说明。

## 9. 开发能力探测与已移除接口

`GET /api/dev/status` 仍需登录：dev profile 返回 active=true，其他 profile 返回 active=false，正常返回而非 500。仅 dev 下存在 GET/POST /api/dev/time、POST /api/dev/time/reset、POST /api/dev/check-in；可调时间及跳过 CF 校验，**不具有生产管理接口的权限边界，不能在公网启用 dev**。

独立签到/签文的 fun-check-ins 接口与自主练习提交校验已移除。平台每日题 check-in 是保留的训练校验功能，不应一起删除。
