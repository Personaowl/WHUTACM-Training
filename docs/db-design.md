# 数据库结构与数据流

[文档索引](README.md) · [接口参考](api-design.md) · [数据迁移](migration.md)

按 2026-10-28 当前 [MySqlInitializer](../backend/src/main/java/com/whut/training/config/MySqlInitializer.java) 和 [Repository](../backend/src/main/java/com/whut/training/repository/) 核对。下文说明表用途、关键字段及约束；完整类型、默认值和建表 SQL 以初始化器为准，避免在文档复制一份容易失效的 DDL。

## 1. 存储与初始化

- 业务库默认名 whut_training，可通过 MYSQL_URL 修改。当前使用 MySQL、InnoDB、utf8mb4_unicode_ci 和 JdbcTemplate，不使用 ORM、SQLite 或 Redis。
- 推荐服务器 MySQL 8.0；开发用 compose.yaml 实际固定 mysql:8.4。迁移时优先同版本恢复演练，不能把二者当作相同版本或直接复制数据目录。
- 启动创建缺失表，并执行代码中列出的补字段、补索引和修正 SQL；**CREATE TABLE IF NOT EXISTS 不代表自动对齐任意旧表结构**，当前没有 Flyway/Liquibase 的版本化迁移体系。
- 当前初始化器创建下列 **24 张表**；旧库可能还有已废弃的表，数量不能作为迁移成功的唯一判据。
- ID 通常为 BIGINT 自增，周记录及 ABC 关系表使用复合主键。代码未声明外键级联约束，关联完整性与删除流程由应用维护，不能假设删父记录会自动删子记录。
- date / week_start 等业务日期为 VARCHAR(10) 的 YYYY-MM-DD；多数过程时间为 VARCHAR(64) 的 ISO 字符串；部分创建时间为 TIMESTAMP，外部平台时间为 BIGINT Unix 秒。恢复必须保留这些语义，业务周按 Asia/Shanghai 计算。

## 2. 用户与认证（2 表）

### users

| 字段组 | 字段与含义 |
| --- | --- |
| 主键与账号 | id；username VARCHAR(64) 非空且唯一；email VARCHAR(255) 可空；password VARCHAR(255) 为 BCrypt 哈希，不是明文 |
| 权限与身份 | role：USER / ADMIN / SUPER_ADMIN；member_type 默认 REGULAR，现役为 ACTIVE_TEAM，两者独立 |
| 个人资料 | display_name VARCHAR(100)、real_name VARCHAR(100)、college / class_name VARCHAR(150)、bio TEXT；学校信息选填 |
| 头像 | avatar_url LONGTEXT 保存自行上传的 data image；avatar_customized 标记；默认头像由前端展示，不保存 CF 外部图片地址 |
| 平台积分与偏好 | total_points INT 默认 0；show_problem_tags 默认 1 |
| CF 快照 | uid、codeforces_rating、max_rating、is_online、last_online_time_seconds；uid 是兼容 CF 信息，不是站内用户主键 |
| CF 绑定 | codeforces_handle（唯一，可空）、pending_codeforces_handle、codeforces_binding_started_at_seconds |
| AtCoder 绑定 | atcoder_handle（唯一，可空）、pending_atcoder_handle、atcoder_binding_token、atcoder_binding_started_at_seconds、atcoder_verified_at_seconds |

关键索引包括 username / 两平台 Handle 的唯一索引，以及 total_points、member_type 索引。接口 DTO 的输入上限可能比存储字段更严格；不要仅按数据库长度推导注册参数。

初始化超级管理员仅在配置的用户名不存在时创建；修改环境变量密码不重设已有用户密码。注册不依赖 CF，头像上传也不依赖 CF；兼容 uid 仍可能从 CF 资料解析，不用于用户登录或头像加载。

新建/修改密码使用 BCrypt；极早期库若仍存明文，登录服务仅在成功登录该账号后升级为哈希，不能据此假定所有历史行早已升级。

### auth_token_session

- 字段：id、user_id、access_token、refresh_token、access_expired_at_seconds、refresh_expired_at_seconds、created_at_seconds。
- 两个 token 列各有唯一约束；**新签发令牌保存 SHA-256 摘要**。Repository 为兼容旧会话仍会按原值/摘要查询，不意味着旧数据库中必然全已转换。
- 过期时间为 Unix 秒，created_at_seconds 沿用首次登录时间。默认 Access 2 小时，会话总长 6 小时；刷新旋转令牌但不续长绝对期限。
- 按用户与过期时间建立索引，定时清除过期会话。会话持久化，所以正常重启不会像内存 token 一样全部丢失；主动退出、超管重置密码或到期会失效。当前普通资料接口修改密码不会自动撤销其他会话，不能把两种操作混为一谈。

### user_platform_binding（新增 1 表）

保存洛谷与牛客验证状态，不改现有 users 字段。字段为 user_id、platform（luogu / nowcoder）、account_id、verified_at、pending_account_id、verification_token、expires_at、started_at、last_attempt_at。主键为 (user_id,platform)，唯一约束 (platform,account_id) 防止重复绑定，未绑定 account_id 为 NULL。时间均为 Unix 秒。

验证中的 UID 与已验证 UID 分开保存；网络读取在事务之外，完成时锁定该绑定行并再次检查验证码/时效，避免旧请求覆盖新挑战。成功清空 pending_account_id 和 verification_token。升级新增该表、不删除旧数据，迁移需随业务库一起备份；验证码仅对本人接口开放，不对其他成员公开。

## 3. 题库、每日题与练习（6 表）

### upsolve_plan（另新增 1 表）

个人补题计划，字段为 id、user_id、title（255）、link（1000）、link_hash（SHA-256）、note（1000）、completed、created_at、completed_at。唯一键 (user_id,link_hash) 防止同一用户重复添加已规范化链接，索引 (user_id,completed,id) 支持分状态分页。完成仅更新状态和完成时间，撤销完成清空完成时间；编辑名称/备注不改变状态；删除不影响已收藏题单。不同用户可保存同一题目。管理员也不能读取或修改他人的记录。

升级自动创建新表，不修改现有每日题或积分记录。完整备份数据库即可迁移补题计划。常见 CF 比赛/题库链接合并为同一题目，AtCoder 和洛谷标准题目链接移除语言参数；其他链接保留查询参数，不抓取外部页面。

### 既有题库与训练表

| 表 | 主要字段 | 主键 / 唯一性与用途 |
| --- | --- | --- |
| cf_problem | id、problem_key、contest_id、problem_index、name、rating、tags、is_interactive、source_contest_id、solved_count、source_url、last_synced_at | problem_key 唯一，通常为 contestId-index；有 rating 和比赛题号索引；仅题目元数据，不存题面、测试数据、代码 |
| daily_problem | id、date、problem_key、contest_id、problem_index、name、rating、tags、source_url、generated_at、generated_by | date 唯一；兼容旧单题快照和 regenerate 接口，仍被代码使用，不能当废表删除 |
| daily_problem_slot | id、date、slot、problem_key、contest_id、problem_index、name、rating、tags、source_url、generated_at、generated_by、is_redrawn | date 索引；当前 Easy/Hard 主表，重抽将旧行 is_redrawn=1；没有 (date,slot) 唯一键，因为保留历次题目 |
| user_daily_status | id、user_id、date、submission_id、verdict、checked_at、score | (user_id,date) 唯一；每日汇总最高分，不是每题一行；有 user_id、(date,user_id)、(user_id,checked_at) 索引，最后一项支持排行时间查询 |
| user_daily_slot_status | id、user_id、date、slot、problem_key、submission_id、verdict、checked_at、score | (user_id,date,problem_key) 唯一；保留题级完成；有 (user_id,date)、problem_key、(date,user_id) 索引 |
| user_practice_draw | id、user_id、draw_date、problem_key、contest_id、problem_index、name、rating、tags、source_url、drawn_at、submission_id、verdict、checked_at | (user_id,draw_date) 索引；仅个人抽题历史，后三个校验字段为旧兼容字段，不代表仍提供练习校验 |

cf_problem 另有 idx_cf_practice_filter (is_interactive,source_contest_id,rating,id) 复合索引，用于练习筛选与有序随机偏移读取。抽题仍需候选计数，完整标签 AND 谓词不是独立索引；真实 MySQL 是否使用该索引应查看执行计划。升级会补建索引，应先备份并选择低峰，避免建索引时的锁与 I/O 开销影响使用。

每日题计分数据流：

1. CF 外部校验在数据库事务之外完成。
2. DailyCheckInRecorder 在短事务中锁 users 对应行，保存题级完成情况。
3. 取已有当天分与全部题级完成分的最大值，更新 user_daily_status。
4. 只将新旧当天分的正差额累加到 users.total_points，避免 Easy + Hard 重复累加。
5. 历史页面仅呈现每个日期/题位的当前有效题，不把旧重抽行展开成更多每日题；旧完成数据保留用于积分与审计。

初始化器会将重复有效题位中较旧的行标为已重抽，保留最大 id 的有效行。当前有效题位约束由应用选择、锁和修正 SQL 维护，**不是数据库唯一约束**；多实例部署不能仅依赖进程锁保证它。

热力图读取 user_daily_status 的每日 score，色阶 0～4 按请求区间的最高分换算；不是独立签到记录，也不是 CF 全部提交数据。历史数据不会随默认 historyRetentionDays=0 自动删除。

## 4. 角色审计（1 表）

| 表 | 主要字段 | 约束与用途 |
| --- | --- | --- |
| role_change_log | id、target_user_id、changed_by、from_role、to_role、changed_at | target_user_id 索引；角色调整审计，不是通用所有管理操作日志 |

旧 push_pool、push_submissions、daily_push 三表如存在，仅保留历史数据。当前不创建、不读写，不执行旧状态修正，不自动删除或迁移内容。

## 5. 平台统计与赛事快照（4 表）

| 表 | 主要字段 | 约束与用途 |
| --- | --- | --- |
| cf_profile_snapshot | user_id、codeforces_handle、payload_json LONGTEXT、synced_at | user_id 主键、Handle 索引；持久化统计快照，配合进程内缓存先读后后台刷新 |
| platform_statistics_snapshot | user_id、platform、account_id、display_name、rating、max_rating、solved_count、profile_info_json、synced_at、partial | (user_id,platform,account_id) 主键；AtCoder / 洛谷 / Nowcoder 本地快照。display_name 为可空 VARCHAR(128)，profile_info_json 为可空 TEXT，保存白名单公开资料，单个文本最多 128 个 Unicode 字符；旧表启动时自动补列，不清空原统计。UID 仍为账号标识，账号隔离，未知指标为 NULL，不存验证码、联系邮箱或网页源码 |
| cf_rating_change | id、user_id、contest_id、contest_name、contest_rank、old_rating、new_rating、rating_change、rating_update_time_seconds、contest_url | (user_id,contest_id) 唯一；时间和 (user_id,时间) 索引；支持 Rated 比赛查询和导出 |
| contest_source_snapshot | platform、payload_json MEDIUMTEXT、synced_at | platform 主键；四平台最近一次成功公开赛程，空的成功列表也会保存，失败不覆盖。重启仅恢复一天内且尚未结束的比赛，并在后台重新验证 |

CF 概览统计最多读取最近 10000 条提交，不能宣称覆盖任意大账号全部历史。每日题校验的“最近 30 条”是另一个独立查询，不影响上述概览上限。

三平台 Rating 曲线的 `ratingHistory` 写入既有 CF / 多平台 JSON 快照，最多最近 500 场；`recentContests` 仍仅保留最近 8 场，用途不同。CF 原 `cf_rating_change` 完整历史及 Excel 导出不受该展示上限影响。旧快照按需后台补齐新字段，无需新增列或迁移业务数据。

## 6. 一级题单（2 表）

### platform_detail_snapshot（另新增 1 表）

非 CF 的平台详情 JSON 快照，字段 user_id、platform、account_id、payload_json MEDIUMTEXT、synced_at，三元组为主键；应用写入限制 256 KiB。保存已公开的难度分布、近期 Rated 比赛和按日活动，缺失分区为 null。升级自动建表、不覆盖已有业务数据；换绑后按新账号键读取，不混入旧账号快照。CF 活动沿用 cf_profile_snapshot 的 JSON，无需补列。完整数据库备份会一并迁移此表。

| 表 | 主要字段 | 约束与用途 |
| --- | --- | --- |
| problem_list | id、owner_user_id、name VARCHAR(80)、description VARCHAR(500)、is_shared、created_at、updated_at | owner_user_id、(is_shared,updated_at) 索引；无 parent_id，不支持嵌套 |
| problem_list_item | id、list_id、added_by BIGINT、title VARCHAR(255)、link VARCHAR(1000)、note VARCHAR(1000)、problem_key、rating、tags、sort_order、created_at | (list_id,sort_order,id)、problem_key 索引；同题单重复链接由服务检查，未建唯一键 |

problem_list 新增可空唯一列 community_key。值 community 的唯一系统题单供全员投稿，首次读取列表时 INSERT IGNORE 创建，其 owner_user_id 仅为存储兼容字段、不授予该用户额外权限；元数据不可修改/删除。其他旧题单该列均为 NULL，is_shared=1 只表示只读分享，只有创建者维护。added_by 仅在公共投稿中授予作者修改权限；管理员可维护公共投稿但不能修改他人只读分享。写操作在事务中锁题单行后检查权限及重复链接。升级不删除/搬迁旧题目，完整备份即可迁移。

## 7. 周训练与说明审核（2 表）

| 表 | 主要字段 | 约束与用途 |
| --- | --- | --- |
| weekly_training_record | week_start、user_id、explanation、submitted_at、review_status、review_note、reviewed_by、reviewed_at、manual_status、version、created_at | (week_start,user_id) 主键；(user_id,week_start) 索引；保存当周名册、私人说明与人工覆盖 |
| weekly_training_audit | id、week_start、user_id、actor_id、action、message、result_status、created_at | (week_start,user_id,id)、(user_id,week_start,id) 索引；界面查询最近 20 条操作历史 |

review_status 默认 NONE，可为 PENDING / APPROVED / REJECTED；manual_status 空值代表 AUTO，非空为 COMPLETED / EXEMPT / INCOMPLETE。version 用于并发审核冲突检查，保存时配合事务与行锁。

每天与 ABC 的自动状态来自本地原始表聚合，不把整份每周报表重复落库。人工覆盖只改变该周最终状态，不改原始完成记录或积分。名册捕获后即使成员退役仍可查该周；启用前未记录的历史身份无法完整还原。

## 8. ABC 跟踪（4 表）

| 表 | 主要字段 | 约束与用途 |
| --- | --- | --- |
| atcoder_contest | contest_id、name、start_time_seconds、end_time_seconds、contest_url、member_snapshot_frozen、sync_status、discovered_at、last_sync_at | contest_id 主键；开赛时间、同步状态索引 |
| atcoder_requirement_member | contest_id、user_id、atcoder_handle_snapshot、required、exempted、exemption_reason、frozen_at | (contest_id,user_id) 主键、user_id 索引；参赛要求与豁免名册 |
| atcoder_participation | contest_id、user_id、participated、contest_rank、performance、is_rated、old_rating、new_rating、ac_count、solved_problem_ids、compliance_status、source_error、checked_at | (contest_id,user_id) 主键；用户和完成状态索引 |
| atcoder_tracking_setting | id、minimum_ac_count、grace_hours、updated_at、updated_by | 单例 id=1，默认至少 AC 1 题、宽限 24 小时 |

同步状态与成员完成状态不是同一枚举：场次有 UPCOMING / SYNCED / FINALIZED；成员有 UNBOUND / UPCOMING / PENDING / COMPLETED / PARTICIPATED / ABSENT / DATA_ERROR，豁免从名册叠加。比赛开赛后首次处理时冻结现役名单；服务停机期间无法精确还原真实开赛瞬间名单。

## 9. 兼容数据与迁移边界

- 旧 user_fun_check_in 表若存在只作历史存档；当前不创建、不读写，也不会自动删除。不应将它写为现行签到模块。
- daily_problem 与练习历史中的旧校验字段仍有兼容意义，不因页面删减就直接删表/列。
- 库内头像、说明、会话摘要和所有业务数据都随完整业务库备份迁移；没有单独的头像 uploads 目录。备份包含私人信息与认证材料，须限制权限、异机保存。
- 进程内校验任务、请求队列和排行榜缓存不在数据库里，重启/迁移后重建。平台统计与赛事来源的持久快照则随整库备份迁移。
- 不要在生产手工按本文重建表，也不要清库“修复结构”。准确操作见 [部署](deployment.md) 与 [停写迁移](migration.md)；上线前备份，在演练库验证目标版本的初始化修正。
