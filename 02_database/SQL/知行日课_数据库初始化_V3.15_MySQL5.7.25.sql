-- 知行日课 V3.15 数据库初始化最终版 / MySQL 5.7.25
-- 用途：仅用于空库首次初始化；不创建数据库，请先选择正确的空库。
-- 内容：V3.0 的 62 表稳定基线 + V3.1/V3.9 词书 3 表 + V3.8/V3.10-V3.15 最终结构与字典。
-- 最终结构：76 张表；DATETIME(3) 存 UTC；业务日期按 Asia/Shanghai。
-- 禁止与旧 R1/V3.0 结构增量脚本混用。已有数据库升级必须先备份并单独评审迁移差异。
-- 演示数据位于脚本末尾，默认关闭；确需写入时，在执行脚本前设置 @include_demo_data := 1。

-- ============================================================================
-- 第一部分 V3.0 稳定空库基线
-- ============================================================================
-- 知行日课 V3.0 数据库初始化最终版；适用 MySQL 5.7.25。
-- 仅用于空库首次初始化：创建62张表并写入3条必需基线数据；不用于已有库升级。
-- 无物理外键；枚举、范围、跨表归属与多态目标由应用事务校验。
-- 不创建数据库；请先选定正确的空库，不要与R1建表或V3结构增量脚本混用。
-- DATETIME(3)统一存UTC，业务日期按Asia/Shanghai；应用生成32位小写十六进制ID。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

CREATE TABLE `app_user` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `seq_no` INT UNSIGNED NULL DEFAULT NULL COMMENT '注册顺序号，从1递增；用于生成短ID',
  `short_id` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '用户可见短ID，格式 R_0001',
  `wx_app_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '微信小程序 AppID。',
  `wx_open_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该 AppID 下的 OpenID；服务端保存。',
  `nickname` VARCHAR(30) NOT NULL DEFAULT '' COMMENT '注册确认时默认微信昵称，允许本人修改；新注册必填1至30字符，历史空值迁移后收紧',
  `birthday` DATE NULL DEFAULT NULL COMMENT '生日日期，格式 YYYY-MM-DD；不含时分秒；不能晚于当前业务日期。',
  `gender` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '性别：0 未填写，1 男，2 女，3 其他；取值由服务端校验。',
  `mobile` VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '注册授权的微信手机号，规范化国际格式；注册完成后业务只读；仅服务端授权流程可首次写入，不向好友返回明文',
  `avatar_url` VARCHAR(2048) NULL DEFAULT NULL COMMENT '头像地址链接；保存完整 HTTPS URL；未设置为 NULL。',
  `real_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '真实名字，最多 50 个字符；未填写为 NULL。',
  `english_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '英语名字，最多 100 个字符；未填写为 NULL。',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active 正常 / disabled 停用 / deleting 删除中 / deleted 待清除；停用或删除中禁止私人访问。',
  `is_owner` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 受邀用户；1 项目负责人；负责人唯一性由准入事务校验。',
  `timezone` VARCHAR(40) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '首版固定业务时区。',
  `current_plan_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前生效计划版本；新建账号初始化事务写入。 逻辑关联 learning_plan.id',
  `row_version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁版本；每次设置修改递增。',
  `ai_consent_version` VARCHAR(40) NULL DEFAULT NULL COMMENT '当前有效 AI 同意文本版本；未同意或撤回为 NULL。',
  `ai_consented_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近有效 AI 同意时间；撤回清空。',
  `load_hint_dismissed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '减负提示最近忽略时间；据此计算 7 日冷却。',
  `last_login_at` DATETIME(3) NULL DEFAULT NULL COMMENT '上次成功登录时间，UTC；每次登录成功后更新，未成功登录为 NULL。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间；首次成功创建小程序账号的时间，UTC；后续登录不修改。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  `mobile_bound_at` DATETIME(3) NULL DEFAULT NULL COMMENT '微信手机号授权绑定时间；历史未授权账号为空',
  `profile_visibility` JSON NULL COMMENT '字段可见范围映射 real_name english_name birthday gender hobbies introduction；private/friends/public；缺省private',
  `hobbies_json` JSON NULL COMMENT '爱好字符串数组；建议最多10个，每个20字符；去重',
  `introduction` VARCHAR(500) NULL DEFAULT NULL COMMENT '自我介绍，最多500字符',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`wx_app_id`, `wx_open_id`) COMMENT '同一微信身份不重复建号',
  UNIQUE KEY `uk_seq_no` (`seq_no`) COMMENT '注册顺序号唯一',
  UNIQUE KEY `uk_short_id` (`short_id`) COMMENT '短ID唯一',
  KEY `idx_2` (`status`) COMMENT '启停和后台任务筛选',
  KEY `idx_mobile` (`mobile`, `status`) COMMENT '手机号精确查找，只返回可添加的脱敏账号资料',
  KEY `idx_nickname` (`nickname`, `id`) COMMENT '昵称前缀查询索引；包含模糊查询仍需受控扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='小程序用户';

CREATE TABLE `user_consent` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户。 逻辑关联 app_user.id',
  `purpose` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'privacy 隐私 / ai_send AI 发送。',
  `document_version` VARCHAR(40) NOT NULL COMMENT '所展示的文本版本。',
  `decision` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'grant 同意 / revoke 撤回。',
  `occurred_at` DATETIME(3) NOT NULL COMMENT '实际发生时间；UTC。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  KEY `idx_1` (`owner_id`, `purpose`, `occurred_at`) COMMENT '按用户与用途读取最近决定'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='同意与撤回记录';

CREATE TABLE `invite_code` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `code_hash` BINARY(32) NOT NULL COMMENT '随机邀请码的 SHA-256；不保存明文。',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'available' COMMENT 'available 可用 / redeemed 已兑换 / revoked 已撤销。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '到期时间；创建时间加 7 天。',
  `redeemed_by` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '兑换用户；注销时清空关联。 逻辑关联 app_user.id',
  `redeemed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '兑换时间。',
  `created_by` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建管理员。 逻辑关联 admin_user.id',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`code_hash`) COMMENT '防止重复邀请码',
  KEY `idx_2` (`status`, `expires_at`) COMMENT '可用性检查和过期清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='一次性邀请码';

CREATE TABLE `admission_counter` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `scope_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'trial' COMMENT '唯一准入范围。',
  `invited_limit` SMALLINT UNSIGNED NOT NULL DEFAULT 20 COMMENT '受邀用户上限。',
  `invited_used` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已占用好友名额；注销完成释放，停用不释放。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`scope_code`) COMMENT '准入事务锁定的唯一计数行'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='试用名额计数';

CREATE TABLE `admin_user` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `login_name` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登录名。',
  `mobile` VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '管理员手机号，按字符串保存，可含国际区号前缀 +；未填写为 NULL。',
  `password_hash` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '含算法及参数的密码哈希；禁止明文或可逆密码。',
  `totp_secret_cipher` VARBINARY(512) NOT NULL COMMENT '两步验证密钥密文；加密密钥在服务端密钥管理中。',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active / disabled。',
  `failed_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连续登录失败次数；5 次锁定。',
  `locked_until` DATETIME(3) NULL DEFAULT NULL COMMENT '锁定截止；默认锁定 15 分钟。',
  `last_login_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近登录。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`login_name`) COMMENT '管理员登录名唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='管理员账号';

CREATE TABLE `auth_session` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `principal_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'user / admin。',
  `principal_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'user 时关联 app_user.id；admin 时关联 admin_user.id。',
  `token_hash` BINARY(32) NOT NULL COMMENT '会话令牌 SHA-256 摘要。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '绝对过期时间。',
  `last_seen_at` DATETIME(3) NOT NULL COMMENT '最近活动；管理员额外执行 30 分钟空闲失效。',
  `revoked_at` DATETIME(3) NULL DEFAULT NULL COMMENT '退出、停用或删除时撤销。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`token_hash`) COMMENT '令牌精确检索',
  KEY `idx_2` (`principal_type`, `principal_id`) COMMENT '批量撤销会话',
  KEY `idx_3` (`expires_at`) COMMENT '清理过期会话'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='登录会话';

CREATE TABLE `learning_plan` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '计划所属用户。 逻辑关联 app_user.id',
  `version_no` INT UNSIGNED NOT NULL COMMENT '用户内单调递增版本。',
  `effective_date` DATE NOT NULL COMMENT '生效业务日期；默认次日，明确调整今日才为当日。',
  `daily_budget_min` TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '允许 5、10、15、20、30 分钟；应用校验。',
  `weekdays_mask` TINYINT UNSIGNED NOT NULL DEFAULT 31 COMMENT '周一至周日对应 bit0 至 bit6；31 为周一至周五；0 暂停。',
  `topic_mask` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '兼容R1旧主题位图；V3只读迁移字段，不再作为主题过滤依据',
  `difficulty` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'intro' COMMENT 'intro 入门 / advanced 进阶。',
  `tech_count` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每日技术新学上限 0—2。',
  `new_word_count` TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '每日新词上限 0—10。',
  `journal_enabled` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否安排每日复盘，0/1。',
  `review_enabled` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否安排复习，0/1。',
  `review_limit` TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '每日计划复习上限 0—10；0 不影响手动浏览。',
  `is_paused` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '计划暂停状态；区分未暂停和无截止的手动暂停。',
  `pause_until` DATE NULL DEFAULT NULL COMMENT '暂停截止日，含该日；is_paused=1 且 NULL 为手动恢复。',
  `change_reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '用户调整原因或初始化标识。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `version_no`) COMMENT '每人计划版本唯一',
  KEY `idx_2` (`owner_id`, `effective_date`, `version_no`) COMMENT '按业务日期选择生效版本'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='学习计划版本';

CREATE TABLE `daily_package` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `business_date` DATE NOT NULL COMMENT '北京时间业务日期。',
  `plan_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '编排依据的计划版本。 逻辑关联 learning_plan.id',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'draft' COMMENT 'draft 次日预编 / active 已激活 / closed 已截止。',
  `is_temporary` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否用户选择今天也学创建的临时活动日。',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '任务包修订版本；调整/系统替换时递增。',
  `budget_seconds` INT UNSIGNED NOT NULL COMMENT '当日预算秒数快照。',
  `original_count` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '首次激活任务数量；激活前 NULL，之后不变。',
  `current_count` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前分母，不含 CANCELLED，包含 SKIPPED。',
  `final_done_count` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '目前有效 DONE 数；补交复盘可增加。',
  `cutoff_total_count` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '截止时分母；未冻结为 NULL。',
  `cutoff_done_count` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '截止时有效完成数；不受之后补交影响。',
  `cutoff_at` DATETIME(3) NULL DEFAULT NULL COMMENT '该业务日结束时间点的 UTC 表示。',
  `metrics_frozen_at` DATETIME(3) NULL DEFAULT NULL COMMENT '按截止事件完成统计冻结时间。',
  `activated_at` DATETIME(3) NULL DEFAULT NULL COMMENT '首次打开并通过校验的激活时间。',
  `gap_json` JSON NULL COMMENT '缺口数组：task_type、gap_count、reason；reason 为素材不足/预算不足/已学完。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `business_date`) COMMENT '防止每日任务重复生成',
  KEY `idx_2` (`state`, `business_date`) COMMENT '激活检查和日截止统计'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='每日任务包';

CREATE TABLE `package_revision` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `package_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任务包。 逻辑关联 daily_package.id',
  `version_no` INT UNSIGNED NOT NULL COMMENT '修订序号。',
  `reason` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'activate / user_adjust / content_withdraw / action_change。',
  `before_json` JSON NULL COMMENT '变更前计划 ID、任务 ID 清单、数量和预算；首版可 NULL。',
  `after_json` JSON NOT NULL COMMENT '变更后上述完整快照。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`package_id`, `version_no`) COMMENT '同包同版本唯一',
  KEY `idx_2` (`owner_id`) COMMENT '账号删除定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='任务包修订快照';

CREATE TABLE `daily_task` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `package_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当日包。 逻辑关联 daily_package.id',
  `task_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'tech / word / journal / review / action。',
  `title_snapshot` VARCHAR(100) NOT NULL COMMENT '任务标题快照；源下架仍能说明历史完成项，私人源删除时清除为通用类型名。',
  `target_key` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定目标键：content:ID、word:词哈希、journal:日期、review:知识ID、action:行动ID。',
  `content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '技术或新词内容；其他类型为空。 逻辑关联 learning_content.id',
  `content_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '安排时发布版本。 逻辑关联 content_version.id',
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '复习目标。 逻辑关联 knowledge_item.id',
  `journal_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '复盘创建后绑定对应日记录。 逻辑关联 daily_journal.id',
  `action_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '周总结行动。 逻辑关联 weekly_action.id',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TODO' COMMENT 'TODO 未开始 / DOING 进行中 / DONE 完成 / SKIPPED 跳过 / CANCELLED 取消。',
  `estimated_seconds` INT UNSIGNED NOT NULL COMMENT '预计耗时快照；不表示真实计时。',
  `sort_no` SMALLINT UNSIGNED NOT NULL COMMENT '编排顺序。',
  `cancel_reason` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'user_remove / source_withdraw / source_delete / action_remove。',
  `started_at` DATETIME(3) NULL DEFAULT NULL COMMENT '首次开始时间。',
  `completed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '当前有效完成时间；撤销完成时清空。',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发更新版本。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`package_id`, `task_type`, `target_key`) COMMENT '同一天同类目标唯一',
  KEY `idx_2` (`owner_id`, `status`) COMMENT '用户任务和删除定位',
  KEY `idx_3` (`content_id`, `status`) COMMENT '下架内容的未完成任务替换',
  KEY `idx_4` (`knowledge_id`) COMMENT '知识删除关联定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='计划任务';

CREATE TABLE `task_event` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `task_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对应任务。 逻辑关联 daily_task.id',
  `task_version` INT UNSIGNED NOT NULL COMMENT '变更后任务版本。',
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'start / complete / undo / skip / cancel / readd。',
  `actor_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'user / system；用户操作主体为 owner_id。',
  `from_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '原状态。',
  `to_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '新状态。',
  `occurred_at` DATETIME(3) NOT NULL COMMENT '服务器接受并生效的时间；不用客户端时间计算准时率。',
  `reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '操作原因；不保存正文。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`task_id`, `task_version`) COMMENT '同一状态版本不重复记事件',
  KEY `idx_2` (`owner_id`, `occurred_at`) COMMENT '截止时间和周统计回溯'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='任务状态事件';

CREATE TABLE `content_source` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `name` VARCHAR(100) NOT NULL COMMENT '来源名称。',
  `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'manual' COMMENT 'manual 人工 / rss 条件能力。',
  `url` VARCHAR(2048) NULL DEFAULT NULL COMMENT '来源或 RSS URL。',
  `allowed_host` VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'RSS 精确允许域名；不存任意抓取表达式。',
  `license_note` VARCHAR(1000) NOT NULL COMMENT '使用许可或授权依据；未知则不能发布来源正文。',
  `enabled` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0 停用 / 1 启用；RSS 同时受全局开关控制。',
  `last_fetched_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近成功采集。',
  `etag` VARCHAR(255) NULL DEFAULT NULL COMMENT 'RSS 条件请求标识。',
  `last_modified` VARCHAR(100) NULL DEFAULT NULL COMMENT 'RSS Last-Modified 标识。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  KEY `idx_1` (`source_type`, `enabled`) COMMENT '来源筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='公开内容来源';

CREATE TABLE `learning_content` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `content_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'tech / word / english_article。',
  `source_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源。 逻辑关联 content_source.id',
  `dedup_hash` BINARY(32) NOT NULL COMMENT '人工导入外部键或规范 URL+正文指纹的 SHA-256；用于导入及 RSS 去重。',
  `origin_url_hash` BINARY(32) NULL DEFAULT NULL COMMENT '规范原始 URL 的 SHA-256；同 URL 新正文优先创建本内容的新版本。',
  `word_key_hash` BINARY(32) NULL DEFAULT NULL COMMENT '规范化词条（去首尾空格、小写）的 SHA-256；仅 word 非空。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'draft' COMMENT 'draft / pending / published / withdrawn。',
  `current_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最新编辑版本。 逻辑关联 content_version.id',
  `published_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前发布版本；草稿或下架无可见版本时 NULL。 逻辑关联 content_version.id',
  `published_at` DATETIME(3) NULL DEFAULT NULL COMMENT '当前发布生效时间，用于内容排序。',
  `withdrawn_at` DATETIME(3) NULL DEFAULT NULL COMMENT '下架时间。',
  `withdraw_reason` VARCHAR(1000) NULL DEFAULT NULL COMMENT '下架或纠错原因，下架时必填。',
  `row_version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '编辑并发版本。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`dedup_hash`) COMMENT '重复导入不增加内容',
  UNIQUE KEY `uk_2` (`word_key_hash`) COMMENT '同一规范词条唯一，非词条允许多个 NULL',
  KEY `idx_3` (`source_id`, `origin_url_hash`) COMMENT '同来源同 URL 的候选版本定位',
  KEY `idx_4` (`content_type`, `state`, `published_at`) COMMENT '内容池筛选和排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='公开学习内容';

CREATE TABLE `content_version` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定内容。 逻辑关联 learning_content.id',
  `version_no` INT UNSIGNED NOT NULL COMMENT '内容内递增版本。',
  `title` VARCHAR(100) NOT NULL COMMENT '标题。',
  `summary` VARCHAR(500) NULL DEFAULT NULL COMMENT '摘要。',
  `body` TEXT NULL COMMENT '技术或英语短文正文；最多 10000 字符。',
  `topic_mask` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '兼容R1旧主题位图；V3主题关系以content_topic为准',
  `difficulty` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'intro' COMMENT 'intro / advanced。',
  `tags_json` JSON NULL COMMENT '公开内容标签字符串数组；不作为 JSON 索引。',
  `estimated_seconds` SMALLINT UNSIGNED NOT NULL DEFAULT 180 COMMENT '技术允许 30—1800；新词为 30。',
  `word_term` VARCHAR(80) NULL DEFAULT NULL COMMENT '英文词条，word 类型必填。',
  `phonetic` VARCHAR(200) NULL DEFAULT NULL COMMENT 'R1兼容音标；V3以pronunciation的口音及词义目标音标为准',
  `meaning` VARCHAR(500) NULL DEFAULT NULL COMMENT 'R1兼容释义；V3结构化词义以word_sense为准，旧客户端只读第一义摘要',
  `example_text` VARCHAR(1000) NULL DEFAULT NULL COMMENT 'R1兼容例句；V3以word_example为准',
  `example_translation` VARCHAR(1000) NULL DEFAULT NULL COMMENT 'R1兼容例句翻译；V3以word_example.translation为准',
  `origin_url` VARCHAR(2048) NULL DEFAULT NULL COMMENT '原文链接。',
  `origin_author` VARCHAR(200) NULL DEFAULT NULL COMMENT '原作者。',
  `origin_published_at` DATETIME(3) NULL DEFAULT NULL COMMENT '原文发布时间，可未知。',
  `license_snapshot` VARCHAR(1000) NOT NULL COMMENT '该版本许可依据；审核时确认。',
  `body_hash` BINARY(32) NOT NULL COMMENT '规范化版本正文指纹。',
  `review_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'draft' COMMENT 'draft / pending / approved / rejected。',
  `reviewed_by` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '审核管理员；可与编辑者相同。 逻辑关联 admin_user.id',
  `reviewed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '审核时间。',
  `review_note` VARCHAR(1000) NULL DEFAULT NULL COMMENT '审核意见。',
  `created_by` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '编辑管理员。 逻辑关联 admin_user.id',
  `ai_job_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '条件 RSS 草稿来源 AI 任务。 逻辑关联 ai_job.id',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  `article_blocks` JSON NULL COMMENT '英语短文段落数组：paragraph_id、原文、译文、关联词条content_id；高亮与译文按版本保持一致',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`content_id`, `version_no`) COMMENT '内容版本唯一',
  KEY `idx_2` (`review_status`, `created_at`) COMMENT '待审核队列'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='学习内容版本';

CREATE TABLE `learning_record` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所学内容。 逻辑关联 learning_content.id',
  `learning_key` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'tech:内容ID、word:词哈希、article:内容ID。',
  `last_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近阅读版本。 逻辑关联 content_version.id',
  `learning_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'learning' COMMENT 'unlearned / learning / understood / mastered；mastered 由用户自评。',
  `first_completed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '第一次明确完成；用于新学去重，撤销首次完成则重算。',
  `last_feedback_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近反馈时间。',
  `review_opt_out` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '1 表示用户不接受自动加入复习。',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  `familiarity_percent` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '自评熟悉度0至100，NULL表示尚未自评；不自动等同学习或复习阶段',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `learning_key`) COMMENT '个人同一学习目标唯一',
  KEY `idx_2` (`content_id`) COMMENT '内容关联定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='个人学习状态';

CREATE TABLE `learning_event` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `record_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '学习状态。 逻辑关联 learning_record.id',
  `record_version` INT UNSIGNED NOT NULL COMMENT '变更后状态版本。',
  `task_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当日存在对应任务时关联；课外学习为空。 逻辑关联 daily_task.id',
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'complete / feedback / undo。',
  `feedback` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'learning / understood / mastered。',
  `business_date` DATE NOT NULL COMMENT '服务器按北京时间计算的实际日期。',
  `occurred_at` DATETIME(3) NOT NULL COMMENT '生效时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`record_id`, `record_version`) COMMENT '幂等事件版本',
  KEY `idx_2` (`owner_id`, `business_date`) COMMENT '周统计与增长日判断'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='个人学习事件';

CREATE TABLE `daily_journal` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `business_date` DATE NOT NULL COMMENT '复盘所属日期，不能未来日期。',
  `current_revision_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前草稿或最近保存版本。 逻辑关联 journal_revision.id',
  `submitted_revision_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最近正式提交版本；草稿更新不覆盖该指针。 逻辑关联 journal_revision.id',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发修订序号。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'draft' COMMENT 'draft 草稿 / submitted 已提交。',
  `first_submitted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '首次有效提交时间；补交不伪造到原日期。',
  `last_submitted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近提交时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `business_date`) COMMENT '同日复盘唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='每日复盘';

CREATE TABLE `journal_revision` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `journal_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属复盘。 逻辑关联 daily_journal.id',
  `revision_no` INT UNSIGNED NOT NULL COMMENT '修订版本。',
  `done_text` TEXT NULL COMMENT '今日完成的事情；对应 PRD done。',
  `blocker_text` TEXT NULL COMMENT '今日阻塞或问题；对应 PRD blocker。',
  `learned_text` TEXT NULL COMMENT '今日收获；对应 PRD learned。',
  `next_step_text` TEXT NULL COMMENT '明日第一件事；对应 PRD next_step。',
  `ai_summary` TEXT NULL COMMENT '用户确认采纳的摘要；不覆盖四问原文。',
  `summary_source_revision` INT UNSIGNED NULL DEFAULT NULL COMMENT '摘要所依据的原文版本；正文变化后用于提示过期。',
  `summary_ai_job_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '采纳来源 AI 任务。 逻辑关联 ai_job.id',
  `content_hash` BINARY(32) NOT NULL COMMENT '四问正文规范化指纹。',
  `save_kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'autosave / manual / submit / accept_summary。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`journal_id`, `revision_no`) COMMENT '修订版本唯一',
  KEY `idx_2` (`owner_id`) COMMENT '私人正文删除定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='复盘修订正文';

CREATE TABLE `knowledge_item` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `item_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'note / problem / content_ref / word / ai_note。',
  `title` VARCHAR(100) NOT NULL COMMENT '标题。',
  `body` TEXT NOT NULL COMMENT '当前正文，最多 10000 字符。',
  `problem_json` JSON NULL COMMENT '问题卡：phenomenon、environment、cause、solution、verification；每项最多 2000，合计最多 10000 字符。',
  `search_text` MEDIUMTEXT NOT NULL COMMENT '正文、问题字段及错误码的规范化搜索文本；仅当前用户内 LIKE 查询，不设置全文索引。',
  `learning_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'unlearned' COMMENT 'unlearned / learning / understood / mastered。',
  `verification_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'unverified' COMMENT 'unverified / verified / partial / invalid；AI 提取默认 unverified。',
  `last_verified_date` DATE NULL DEFAULT NULL COMMENT '最后主动验证日期；verified/partial 须有验证说明。',
  `mastered_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近一次用户自评掌握时间。',
  `reuse_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户主动记录的成功复用次数；不以访问次数推断。',
  `last_reused_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近一次主动标记成功复用时间。',
  `source_content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '公开内容来源；独立笔记为空。 逻辑关联 learning_content.id',
  `source_content_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '引用内容版本，不复制公开全文。 逻辑关联 content_version.id',
  `source_journal_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '复盘来源；删除来源时清理。 逻辑关联 daily_journal.id',
  `source_journal_revision` INT UNSIGNED NULL DEFAULT NULL COMMENT '提取所依据的复盘版本。',
  `source_ai_job_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'AI 采纳来源；只做来源标记，不依赖临时 AI 正文长期存在。 逻辑关联 ai_job.id',
  `bookmark_content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '有效收藏对应内容；取消收藏清空并保留自写笔记。 逻辑关联 learning_content.id',
  `word_key_hash` BINARY(32) NULL DEFAULT NULL COMMENT '生词本规范词键；仅 word 非空。',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁及历史修订序号。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  `visibility` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'private' COMMENT 'private仅自己；friends所有当前好友含未来新增；selected指定好友',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'draft草稿；active有效；deleted不可访问并进入清理',
  `note_parent_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '做笔记时关联原知识；只读引用，正文独立保存；原文删除后不扩大访问 逻辑关联 knowledge_item.id',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `bookmark_content_id`) COMMENT '同用户有效收藏唯一；NULL 可多条',
  UNIQUE KEY `uk_2` (`owner_id`, `word_key_hash`) COMMENT '同用户生词唯一；NULL 可多条',
  KEY `idx_3` (`owner_id`, `item_type`, `updated_at`) COMMENT '知识列表筛选',
  KEY `idx_4` (`source_journal_id`) COMMENT '源复盘删除和变更定位',
  KEY `idx_5` (`source_content_id`) COMMENT '公开内容下架引用定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='个人知识条目';

CREATE TABLE `knowledge_revision` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '知识条目。 逻辑关联 knowledge_item.id',
  `revision_no` INT UNSIGNED NOT NULL COMMENT '修订号。',
  `snapshot_json` JSON NOT NULL COMMENT '与当前知识字段同语义的完整快照，包括 tags 数组。',
  `change_kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'create / edit / feedback / verify / accept_ai / detach_source。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`knowledge_id`, `revision_no`) COMMENT '修订唯一',
  KEY `idx_2` (`owner_id`) COMMENT '账号删除定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识修订快照';

CREATE TABLE `knowledge_tag` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '知识条目；必须属于 owner_id。 逻辑关联 knowledge_item.id',
  `tag_name` VARCHAR(20) NOT NULL COMMENT '去首尾空格后的标签；按 utf8mb4_unicode_ci 大小写不敏感。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`knowledge_id`, `tag_name`) COMMENT '同条目标签去重',
  KEY `idx_2` (`owner_id`, `tag_name`) COMMENT '个人标签筛选与搜索'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识标签关联';

CREATE TABLE `review_schedule` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标知识。 逻辑关联 knowledge_item.id',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active / paused / completed；暂停保留到期日。',
  `stage` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前阶段 0—3。',
  `due_date` DATE NULL DEFAULT NULL COMMENT '下次到期日；新加入为次日，completed 时为 NULL。',
  `last_feedback_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最近有效反馈日记录。 逻辑关联 review_feedback.id',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '调度并发版本。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `knowledge_id`) COMMENT '复习目标唯一',
  KEY `idx_2` (`owner_id`, `state`, `due_date`, `knowledge_id`) COMMENT '稳定排序读取到期队列'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识复习队列';

CREATE TABLE `review_feedback` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '知识条目。 逻辑关联 knowledge_item.id',
  `schedule_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '复习调度。 逻辑关联 review_schedule.id',
  `business_date` DATE NOT NULL COMMENT '实际反馈日期；首版不允许补交历史学习反馈。',
  `feedback` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'remember 记住 / fuzzy 模糊 / unclear 没懂。',
  `before_stage` TINYINT UNSIGNED NOT NULL COMMENT '当天首次反馈前阶段。',
  `before_state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当天首次反馈前调度状态。',
  `before_due_date` DATE NULL DEFAULT NULL COMMENT '当天首次反馈前到期日。',
  `after_stage` TINYINT UNSIGNED NOT NULL COMMENT '当前有效反馈产生的新阶段。',
  `after_state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '新调度状态。',
  `after_due_date` DATE NULL DEFAULT NULL COMMENT '新到期日；completed 为 NULL。',
  `revision_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '当天反馈修订号。',
  `task_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当日计划内对应复习任务；课外为空。 逻辑关联 daily_task.id',
  `effective_at` DATETIME(3) NOT NULL COMMENT '当前反馈生效时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `knowledge_id`, `business_date`) COMMENT '每日有效反馈唯一',
  KEY `idx_2` (`owner_id`, `business_date`) COMMENT '周汇总',
  KEY `idx_3` (`schedule_id`) COMMENT '队列关联'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='复习日反馈';

CREATE TABLE `review_feedback_revision` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `feedback_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '日反馈。 逻辑关联 review_feedback.id',
  `revision_no` INT UNSIGNED NOT NULL COMMENT '版本。',
  `feedback` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'remember / fuzzy / unclear。',
  `after_stage` TINYINT UNSIGNED NOT NULL COMMENT '重算阶段。',
  `after_state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '重算状态。',
  `after_due_date` DATE NULL DEFAULT NULL COMMENT '重算到期日。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`feedback_id`, `revision_no`) COMMENT '修订唯一',
  KEY `idx_2` (`owner_id`) COMMENT '账号删除定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='复习反馈修订';

CREATE TABLE `weekly_summary` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `week_start` DATE NOT NULL COMMENT '该周周一日期。',
  `current_revision_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前展示修订。 逻辑关联 weekly_revision.id',
  `confirmed_revision_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前有效已确认修订；源删除导致失效时清空。 逻辑关联 weekly_revision.id',
  `source_fingerprint` BINARY(32) NOT NULL COMMENT '源记录 ID、版本、统计口径规范化指纹。',
  `is_stale` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否有新数据使当前总结待更新。',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `week_start`) COMMENT '每周仅一份总结'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='周总结';

CREATE TABLE `weekly_revision` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `summary_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '周总结。 逻辑关联 weekly_summary.id',
  `revision_no` INT UNSIGNED NOT NULL COMMENT '修订序号。',
  `metrics_json` JSON NOT NULL COMMENT '任务分母、准时完成、最终完成、复习数、有效增长日等结构化指标快照。',
  `body` TEXT NULL COMMENT '用户编辑或确认的总结正文。',
  `sources_json` JSON NOT NULL COMMENT '所引用日复盘、知识 ID 和版本数组；不复制源正文。',
  `source_fingerprint` BINARY(32) NOT NULL COMMENT '该修订的来源指纹。',
  `ai_job_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '润色来源 AI 任务。 逻辑关联 ai_job.id',
  `confirmed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '明确确认时间；未确认为 NULL。',
  `invalidated_at` DATETIME(3) NULL DEFAULT NULL COMMENT '因来源删除而清除正文、失效的时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`summary_id`, `revision_no`) COMMENT '同周修订唯一',
  KEY `idx_2` (`owner_id`) COMMENT '源删除扫描及账号清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='周总结修订';

CREATE TABLE `weekly_action` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户。 逻辑关联 app_user.id',
  `summary_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源周总结。 逻辑关联 weekly_summary.id',
  `action_no` TINYINT UNSIGNED NOT NULL COMMENT '周内槽位 1—3；删除后复用槽位及 ID。',
  `title` VARCHAR(100) NOT NULL COMMENT '行动标题。',
  `note` VARCHAR(1000) NULL DEFAULT NULL COMMENT '行动说明。',
  `scheduled_date` DATE NOT NULL COMMENT '安排日期；北京时间。',
  `estimated_minutes` TINYINT UNSIGNED NOT NULL COMMENT '预计 1—30 分钟。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'draft' COMMENT 'draft / confirmed / cancelled。',
  `confirmed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '确认时间。',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`summary_id`, `action_no`) COMMENT '每周行动槽位唯一，范围由应用校验',
  KEY `idx_2` (`owner_id`, `scheduled_date`, `state`) COMMENT '编排当日行动'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='下周行动';

CREATE TABLE `ai_model_price` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `provider_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '供应商代号。',
  `model_code` VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模型标识。',
  `version_no` INT UNSIGNED NOT NULL COMMENT '价格配置递增版本。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ISO 货币代码；与预算币种一致。',
  `input_per_million` DECIMAL(18,6) NOT NULL COMMENT '每百万输入 token 单价，不得为负。',
  `output_per_million` DECIMAL(18,6) NOT NULL COMMENT '每百万输出 token 单价，不得为负。',
  `pricing_json` JSON NOT NULL COMMENT '完整收费规则快照：缓存或其他收费项；未知规则禁止启用。',
  `effective_at` DATETIME(3) NOT NULL COMMENT '价格版本生效时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`provider_code`, `model_code`, `version_no`) COMMENT '价格快照唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='模型价格版本';

CREATE TABLE `ai_job` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '用户 AI 任务必填；公开 RSS 草稿为空。 逻辑关联 app_user.id',
  `scope_key` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID；公开任务为 32 个 0。',
  `action_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'explain_content/refine_journal/extract_problem/organize_note/polish_week；新增定时公共资源摘要resource_summary，按明确配置授权执行',
  `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'content / journal / knowledge / week / rss_candidate。',
  `source_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所选源对象 ID；按 source_type 关联对应表，RSS 候选引用后台作业 ID。',
  `source_version` INT UNSIGNED NOT NULL COMMENT '输入所依据的源修订。',
  `source_fingerprint` BINARY(32) NOT NULL COMMENT '选中输入及源版本指纹；采纳时重新比对。',
  `consent_version` VARCHAR(40) NULL DEFAULT NULL COMMENT '用户触发时有效 AI 同意版本；公开任务为空。',
  `prompt_version` VARCHAR(40) NOT NULL COMMENT '提示词模板版本。',
  `input_text` TEXT NULL COMMENT '用户已预览的输入，最多 8000 字符；清理后 NULL。',
  `output_json` JSON NULL COMMENT '通过结构校验的临时结果；清理后 NULL。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'queued' COMMENT 'queued / running / succeeded / failed / cancelled。',
  `cancel_requested` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '运行中取消请求；结果返回后不得自动保存。',
  `attempt_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本任务累计外部请求尝试数；自动重试最多 1 次，手动重试另记尝试并重新检查额度。',
  `queue_expires_at` DATETIME(3) NOT NULL COMMENT '入队后 10 分钟失效。',
  `payload_expires_at` DATETIME(3) NOT NULL COMMENT '临时正文清除截止，任务结束后最多 24 小时；删除请求可提前。',
  `accepted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '用户明确采纳时间；锁任务行保证只采纳一次。',
  `accepted_target_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'journal_revision / knowledge / weekly_revision / content_version。',
  `accepted_target_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '采纳后的业务对象 ID；来源删除时清空。',
  `error_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '稳定错误码；不含供应商原文或私人输入。',
  `request_key_hash` BINARY(32) NOT NULL COMMENT '入口 Idempotency-Key 摘要。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`scope_key`, `action_code`, `request_key_hash`) COMMENT '创建任务幂等',
  KEY `idx_2` (`owner_id`, `created_at`) COMMENT '用户任务查询及删除',
  KEY `idx_3` (`state`, `queue_expires_at`) COMMENT '待执行任务',
  KEY `idx_4` (`payload_expires_at`) COMMENT '临时正文清理',
  KEY `idx_5` (`source_type`, `source_id`) COMMENT '来源变更及删除传播'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='场景 AI 任务';

CREATE TABLE `ai_attempt` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `job_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '所属 AI 任务；私人任务删除后清空，仅保留匿名费用。 逻辑关联 ai_job.id',
  `attempt_no` INT UNSIGNED NOT NULL COMMENT '任务内单调递增请求序号；自动重试和手动重试均不复用序号。',
  `trigger_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'initial 首次 / auto_retry 自动重试 / manual_retry 手动重试。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '用户任务归属；注销时清空；公开任务为 NULL。 逻辑关联 app_user.id',
  `price_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次冻结的价格版本。 逻辑关联 ai_model_price.id',
  `budget_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际发起月份的月预算。 逻辑关联 ai_month_budget.id',
  `quota_date` DATE NOT NULL COMMENT '实际发起请求的北京时间日期，重试跨日重新计算。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'reserved' COMMENT 'reserved 预留 / sending / succeeded / failed / unknown / cancelled。',
  `provider_request_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '服务商请求标识；注销时清空。',
  `reserved_amount` DECIMAL(18,6) NOT NULL COMMENT '本次最大费用预留；不得负数。',
  `settled_amount` DECIMAL(18,6) NULL DEFAULT NULL COMMENT '核定费用；未知时 NULL，继续保留预留占用。',
  `input_tokens` INT UNSIGNED NULL DEFAULT NULL COMMENT '实际输入 token；未知为 NULL。',
  `output_tokens` INT UNSIGNED NULL DEFAULT NULL COMMENT '实际输出 token；未知为 NULL。',
  `usage_json` JSON NULL COMMENT '供应商 usage 原始计量字段及折扣结算信息；仅计量，不保存正文。',
  `started_at` DATETIME(3) NULL DEFAULT NULL COMMENT '真正发出时间；发送前消耗日次数。',
  `finished_at` DATETIME(3) NULL DEFAULT NULL COMMENT '结束时间。',
  `timeout_at` DATETIME(3) NULL DEFAULT NULL COMMENT '请求超时上限，发起后 60 秒。',
  `quota_reserved` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否仍占日次数预留；发送时转为已用。',
  `concurrency_held` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否占并发名额；供应商确认终止后原子释放，取消请求或未知状态不能直接释放。',
  `reconciled_at` DATETIME(3) NULL DEFAULT NULL COMMENT '费用对账完成时间。',
  `error_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '脱敏失败类别。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`job_id`, `attempt_no`) COMMENT '同一次请求不重复建账，job_id 清空后不再幂等重试',
  KEY `idx_2` (`budget_id`, `state`) COMMENT '预算对账',
  KEY `idx_3` (`owner_id`) COMMENT '注销去关联',
  KEY `idx_4` (`state`, `timeout_at`) COMMENT '超时与预留恢复'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI 请求尝试与费用';

CREATE TABLE `ai_month_budget` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `month_start` DATE NOT NULL COMMENT '北京时间自然月第一天。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '与价格版本同币种。',
  `limit_amount` DECIMAL(18,6) NOT NULL COMMENT '人工配置的月额度；无默认值，未配置不可发起 AI。',
  `reserved_amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '尚未结算的全部预留；未知费用继续占用。',
  `spent_amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '已确认费用合计。',
  `warned_at` DATETIME(3) NULL DEFAULT NULL COMMENT '本月首次达到 80% 的提醒时间。',
  `row_version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '预算并发版本。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`month_start`) COMMENT '每月全局一个预算'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='月度 AI 预算';

CREATE TABLE `ai_daily_quota` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `quota_date` DATE NOT NULL COMMENT '北京时间自然日。',
  `scope_key` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID 为个人额度；32 个 0 为全局额度。',
  `limit_count` INT UNSIGNED NOT NULL COMMENT '个人默认 10；全局默认 200；公共草稿只计全局。',
  `used_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已经实际发出的请求数。',
  `reserved_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '正在准备发送而预留的请求数。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`quota_date`, `scope_key`) COMMENT '个人/全局日计数唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='每日 AI 次数';

CREATE TABLE `ai_concurrency_guard` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `scope_key` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID 或全局 32 个 0。',
  `limit_count` TINYINT UNSIGNED NOT NULL COMMENT '个人默认 1，全局默认 2。',
  `running_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已预留或在途数量；与 ai_attempt.concurrency_held 保持一致。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`scope_key`) COMMENT '并发锁定行唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI 并发计数';

CREATE TABLE `background_job` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `job_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'next_day / week_summary / cleanup / delete / ai_dispatch / import / rss / remind / export / day_close。',
  `dedup_hash` BINARY(32) NOT NULL COMMENT '稳定作业键 SHA-256：类型+目标+业务日期或目标版本。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '个人作业所属用户；全局作业为空。 逻辑关联 app_user.id',
  `target_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '目标对象类型。',
  `target_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '目标对象 ID；按类型关联。',
  `payload_json` JSON NULL COMMENT '最小必要参数、导入逐行结果或 RSS 临时候选；私人正文禁止写入，公开导入载荷处理后清理。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'queued' COMMENT 'queued / running / succeeded / failed / cancelled。',
  `attempt_count` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '工作执行次数，不等同 AI 外部请求次数。',
  `max_attempts` SMALLINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '最多执行次数；按任务类型配置。',
  `run_after` DATETIME(3) NOT NULL COMMENT '最早可执行时间。',
  `lease_owner` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '领取工作实例标识。',
  `lease_until` DATETIME(3) NULL DEFAULT NULL COMMENT '租约截止；超时需核对副作用后恢复。',
  `finished_at` DATETIME(3) NULL DEFAULT NULL COMMENT '终结时间。',
  `error_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '错误类别，无私人正文。',
  `expires_at` DATETIME(3) NULL DEFAULT NULL COMMENT '终结后元数据清理时间，默认不超过 30 日；删除作业随删除状态保留。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`dedup_hash`) COMMENT '后台作业幂等',
  KEY `idx_2` (`state`, `run_after`) COMMENT '任务领取',
  KEY `idx_3` (`owner_id`) COMMENT '用户停用/删除关联',
  KEY `idx_4` (`expires_at`) COMMENT '运行记录清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='后台作业与事务出箱';

CREATE TABLE `api_idempotency` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `principal_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'user / admin。',
  `principal_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对应用户或管理员 ID。',
  `route_hash` BINARY(32) NOT NULL COMMENT 'HTTP 方法+规范化路由的 SHA-256。',
  `key_hash` BINARY(32) NOT NULL COMMENT '客户端幂等键 SHA-256。',
  `request_hash` BINARY(32) NOT NULL COMMENT '规范请求体摘要；同键不同内容返回冲突。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'processing' COMMENT 'processing / completed / failed。',
  `result_code` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '原请求 HTTP 结果码。',
  `result_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '结果资源类型。',
  `result_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '结果资源 ID；重读必须重新鉴权。',
  `result_version` INT UNSIGNED NULL DEFAULT NULL COMMENT '已提交的结果版本。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '记录创建后 7 天。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`principal_type`, `principal_id`, `route_hash`, `key_hash`) COMMENT '同主体同路由同键唯一',
  KEY `idx_2` (`expires_at`) COMMENT '清理过期记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='API 幂等记录';

CREATE TABLE `admin_audit` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `admin_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作管理员。 逻辑关联 admin_user.id',
  `action_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'publish / withdraw / source_edit / user_status / invite / budget / config / job_retry。',
  `target_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标对象类型。',
  `target_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '目标 ID；涉及已注销用户的可识别关联须去除。',
  `result_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'success / rejected / failed。',
  `metadata_json` JSON NULL COMMENT '变更字段名、版本、状态及数量等脱敏信息。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '默认创建后 180 天。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  KEY `idx_1` (`admin_id`, `created_at`) COMMENT '管理员审计查询',
  KEY `idx_2` (`target_type`, `target_id`) COMMENT '目标审计查询',
  KEY `idx_3` (`expires_at`) COMMENT '审计保留期清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='管理操作审计';

CREATE TABLE `user_feedback` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交用户。 逻辑关联 app_user.id',
  `category` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'bug / suggestion / content_error / copyright。',
  `content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '内容报错或版权反馈的目标内容；其他反馈为空。 逻辑关联 learning_content.id',
  `body` TEXT NOT NULL COMMENT '反馈正文；最多 2000 字符。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'open' COMMENT 'open / processing / resolved / rejected。',
  `request_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '用户引用的错误请求标识。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  KEY `idx_1` (`owner_id`, `created_at`) COMMENT '个人反馈记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户反馈';

CREATE TABLE `data_export` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据归属。 逻辑关联 app_user.id',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'queued' COMMENT 'queued / running / ready / failed / expired / cancelled。',
  `object_key` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '私有导出文件存储键；过期或删除后清空。',
  `file_size_bytes` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '文件字节数。',
  `file_hash` BINARY(32) NULL DEFAULT NULL COMMENT '文件 SHA-256。',
  `snapshot_at` DATETIME(3) NULL DEFAULT NULL COMMENT '导出数据快照时间。',
  `ready_at` DATETIME(3) NULL DEFAULT NULL COMMENT '导出完成时间。',
  `expires_at` DATETIME(3) NULL DEFAULT NULL COMMENT 'ready 后 24 小时失效并删除文件。',
  `error_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '失败类别。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  KEY `idx_1` (`owner_id`, `created_at`) COMMENT '个人导出进度',
  KEY `idx_2` (`state`, `expires_at`) COMMENT '过期文件清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='数据导出任务';

CREATE TABLE `data_deletion` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '请求时用户 ID；账号主库清除后置 NULL。 逻辑关联 app_user.id',
  `scope_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'account / journal / knowledge。',
  `target_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '待删对象 ID；主库清除后清空。',
  `receipt_hash` BINARY(32) NOT NULL COMMENT '随机查询凭据摘要；凭据仅交给本人，用于账号删除后的进度查询。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'requested' COMMENT 'requested / blocked / cleaning / waiting_backup / completed / failed。',
  `primary_deadline_at` DATETIME(3) NOT NULL COMMENT '主库、缓存、临时文件与派生正文清除截止，申请后 24 小时。',
  `primary_cleaned_at` DATETIME(3) NULL DEFAULT NULL COMMENT '主数据确认清除时间。',
  `backup_clear_after` DATETIME(3) NOT NULL COMMENT '最后含该数据的备份到期时间；备份保留 7 天。',
  `completed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '所有删除分项完成时间。',
  `receipt_expires_at` DATETIME(3) NULL DEFAULT NULL COMMENT '申请后 30 天的只读凭据到期；任务未完成仍保留脱敏执行状态，不延长原凭据权限。',
  `error_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '失败类别或供应商待确认类别。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`receipt_hash`) COMMENT '匿名查询凭据唯一',
  KEY `idx_2` (`owner_id`, `state`) COMMENT '查询个人删除请求',
  KEY `idx_3` (`state`, `primary_deadline_at`) COMMENT '清理超时监控'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='删除任务';

CREATE TABLE `deletion_step` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `deletion_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '删除任务。 逻辑关联 data_deletion.id',
  `step_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'primary / derived / cache / export / provider / backup。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'pending' COMMENT 'pending / running / completed / not_applicable / failed。',
  `provider_receipt` VARCHAR(255) NULL DEFAULT NULL COMMENT '第三方删除确认编号；可能含关联的编号在完成核验后清空。',
  `attempt_count` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '处理次数。',
  `last_attempt_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近尝试时间。',
  `completed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '完成时间。',
  `error_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '脱敏错误类别。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`deletion_id`, `step_code`) COMMENT '每任务每分项唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='删除分项状态';

CREATE TABLE `deletion_tombstone` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `deletion_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属删除任务。 逻辑关联 data_deletion.id',
  `object_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'account / journal / knowledge；派生对象按源关系清理。',
  `object_id_hash` BINARY(32) NOT NULL COMMENT '对象类型和原 ID 的 HMAC-SHA256；不保存原 ID/微信身份。',
  `hmac_key_version` VARCHAR(40) NOT NULL COMMENT '服务端 HMAC 密钥版本。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '申请后至少 30 天且备份清除已确认；失败任务延长保留。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`object_type`, `object_id_hash`) COMMENT '删除对象墓碑唯一',
  KEY `idx_2` (`expires_at`) COMMENT '安全保留期后清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='删除恢复拦截标识';

CREATE TABLE `notification_setting` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户。 逻辑关联 app_user.id',
  `enabled` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否启用每日汇总提醒。',
  `minute_of_day` SMALLINT UNSIGNED NOT NULL DEFAULT 1230 COMMENT '北京时间分钟数；20:30 为 1230；允许 480—1320，步长 30。',
  `template_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '微信已配置模板 ID。',
  `authorization_state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'unknown' COMMENT 'unknown / accepted / rejected / revoked；不代表永久发送权限。',
  `authorized_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近平台授权时间。',
  `authorization_json` JSON NULL COMMENT '平台实际返回的必要授权信息，不推断可发送次数。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`) COMMENT '每用户一个提醒设置'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='微信提醒设置〔条件能力〕';

CREATE TABLE `notification_delivery` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键；服务端生成的 32 位小写十六进制随机 ID。',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接收用户。 逻辑关联 app_user.id',
  `business_date` DATE NOT NULL COMMENT '发送所属北京时间日期。',
  `notification_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'digest' COMMENT '每日汇总。',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'pending' COMMENT 'pending / sending / sent / ineligible / cancelled / failed / unknown。',
  `template_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次模板 ID。',
  `scheduled_at` DATETIME(3) NOT NULL COMMENT '计划发送时刻。',
  `sent_at` DATETIME(3) NULL DEFAULT NULL COMMENT '确认发送成功的时间。',
  `provider_message_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '平台返回消息标识。',
  `error_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '错误/跳过原因；不保存提醒正文。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '发送元数据默认保留 30 天；账号删除立即清理。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间；UTC。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间；UTC；ON UPDATE CURRENT_TIMESTAMP(3)。',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `business_date`, `notification_type`) COMMENT '每日发送防重',
  KEY `idx_2` (`state`, `scheduled_at`) COMMENT '待发送查询',
  KEY `idx_3` (`expires_at`) COMMENT '保留期清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='微信提醒发送记录〔条件能力〕';

CREATE TABLE `learning_topic` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `scope_key` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'system或用户ID；避免可空唯一键问题',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '个人自定义主题所属用户；系统主题为空 逻辑关联 app_user.id',
  `name` VARCHAR(50) NOT NULL COMMENT '主题名称1至50字符',
  `normalized_name` VARCHAR(50) NOT NULL COMMENT '去前后空格并规范化名称',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`scope_key`, `normalized_name`) COMMENT '同一范围主题名称不重复'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='学习主题';

CREATE TABLE `learning_plan_topic` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `plan_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联learning_plan 逻辑关联 learning_plan.id',
  `topic_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联learning_topic 逻辑关联 learning_topic.id',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`plan_id`, `topic_id`) COMMENT '计划版本选中主题去重'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='计划选择主题';

CREATE TABLE `content_topic` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `content_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联content_version 逻辑关联 content_version.id',
  `topic_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联learning_topic 逻辑关联 learning_topic.id',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`content_version_id`, `topic_id`) COMMENT '版本主题关系去重'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='内容版本主题';

CREATE TABLE `word_sense` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `content_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联content_version 逻辑关联 content_version.id',
  `part_of_speech` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'noun/verb/adjective/adverb/preposition/other；只录真实词性',
  `meaning` VARCHAR(1000) NOT NULL COMMENT '此词性下的一项中文释义',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`content_version_id`, `sort_no`) COMMENT '版本内词义顺序唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='单词词义分组';

CREATE TABLE `word_example` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `sense_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联word_sense 逻辑关联 word_sense.id',
  `sentence` VARCHAR(1000) NOT NULL COMMENT '原文例句',
  `translation` VARCHAR(1000) NOT NULL COMMENT '中文译文',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '例句顺序',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`sense_id`, `sort_no`) COMMENT '词义内例句顺序唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='词义例句';

CREATE TABLE `media_asset` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '私人上传所属用户；公共资源为空 逻辑关联 app_user.id',
  `purpose` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'avatar/knowledge_image/word_audio/example_audio/article_audio/follow_recording',
  `object_key` VARCHAR(512) NOT NULL COMMENT '私有存储对象键，不存永久签名地址',
  `mime_type` VARCHAR(80) NOT NULL COMMENT '允许的MIME类型',
  `byte_size` BIGINT UNSIGNED NOT NULL COMMENT '文件字节数',
  `sha256` BINARY(32) NOT NULL COMMENT '文件内容摘要',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'pending' COMMENT 'pending/ready/rejected/deleted',
  `origin_url` VARCHAR(2048) NULL DEFAULT NULL COMMENT '有授权的外部来源',
  `license_note` VARCHAR(1000) NULL DEFAULT NULL COMMENT '使用来源与许可说明',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  KEY `idx_1` (`owner_id`, `purpose`, `state`) COMMENT '用户资源清理与选择'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='媒体资源';



-- ---- V3.15 跟读录音私有云保存 ----
CREATE TABLE `follow_recording` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户 逻辑关联 app_user.id',
  `content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '英语短文 逻辑关联 learning_content.id',
  `content_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '录制时短文版本 逻辑关联 content_version.id',
  `asset_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OSS私有录音资源 逻辑关联 media_asset.id',
  `duration_ms` INT UNSIGNED NOT NULL COMMENT '录音时长毫秒，1至60000',
  `state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active/deleted',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
  `deleted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '用户删除时间 UTC',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_follow_recording_owner_content` (`owner_id`,`content_id`) COMMENT '每位用户每篇短文只保留最新录音',
  KEY `idx_follow_recording_asset` (`asset_id`,`state`) COMMENT '资源回收与状态核对',
  KEY `idx_follow_recording_owner_state` (`owner_id`,`state`) COMMENT '用户数据导出和清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='英语短文跟读录音';

CREATE TABLE `pronunciation` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `content_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联content_version 逻辑关联 content_version.id',
  `sense_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联word_sense 逻辑关联 word_sense.id',
  `example_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联word_example 逻辑关联 word_example.id',
  `target_key` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'word或sense:词义ID或example:例句ID；异词性读音绑定sense',
  `accent` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'uk/us',
  `phonetic` VARCHAR(200) NULL DEFAULT NULL COMMENT '此口音对应音标',
  `asset_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联media_asset 逻辑关联 media_asset.id',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ready' COMMENT 'ready/unavailable',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`content_version_id`, `target_key`, `accent`) COMMENT '同版本同目标同口音一条发音'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='单词及例句发音';

CREATE TABLE `word_notebook` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户 逻辑关联 app_user.id',
  `content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联learning_content 逻辑关联 learning_content.id',
  `word_key_hash` BINARY(32) NOT NULL COMMENT '与原单词规范键一致',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active/removed',
  `added_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '本次加入时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `word_key_hash`) COMMENT '同一用户同单词不重复加入',
  KEY `idx_2` (`owner_id`, `state`, `added_at`) COMMENT '生词本分页'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='个人生词本';

CREATE TABLE `user_favorite` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户 逻辑关联 app_user.id',
  `target_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'content/word/article/resource/photo/knowledge',
  `target_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '由target_type决定逻辑目标；不跨类型混用',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active/removed',
  `title_snapshot` VARCHAR(200) NOT NULL COMMENT '失效时只展示合法最小标题快照',
  `favorited_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近收藏时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`owner_id`, `target_type`, `target_id`) COMMENT '幂等收藏',
  KEY `idx_2` (`owner_id`, `state`, `favorited_at`, `id`) COMMENT '收藏游标分页'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='个人收藏';

CREATE TABLE `friend_relation` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `user_low_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联app_user 逻辑关联 app_user.id',
  `user_high_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联app_user 逻辑关联 app_user.id',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'none' COMMENT 'none/active/removed；双方ID按字典序排列',
  `generation` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每次重新建立关系加1，旧指定授权不复活',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  `accepted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近建立好友时间',
  `removed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '解除时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`user_low_id`, `user_high_id`) COMMENT '同一无序用户对唯一关系',
  KEY `idx_2` (`user_high_id`, `state`) COMMENT '高位用户好友列表',
  KEY `idx_3` (`user_low_id`, `state`) COMMENT '低位用户好友列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='好友关系与申请串行锁';

CREATE TABLE `friend_request` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `relation_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联friend_relation 逻辑关联 friend_relation.id',
  `sender_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联app_user 逻辑关联 app_user.id',
  `receiver_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联app_user 逻辑关联 app_user.id',
  `remark` VARCHAR(200) NOT NULL COMMENT '申请备注必填1至200字符',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/rejected/invalid',
  `pending_slot` TINYINT UNSIGNED NULL DEFAULT 1 COMMENT 'pending固定1，其他状态必须NULL；唯一键利用NULL实现多历史一待处理',
  `decided_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最终处理时间',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观并发版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`relation_id`, `pending_slot`) COMMENT '同一用户对最多一条待处理',
  KEY `idx_2` (`receiver_id`, `state`, `created_at`, `id`) COMMENT '收到申请列表',
  KEY `idx_3` (`sender_id`, `state`, `created_at`, `id`) COMMENT '发出申请列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='好友申请与处理';

CREATE TABLE `user_inbox_event` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户 逻辑关联 app_user.id',
  `event_key` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '如requestID:accepted:recipientID',
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'friend_requested/friend_accepted/friend_rejected/share_changed',
  `target_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联申请或知识ID',
  `read_at` DATETIME(3) NULL DEFAULT NULL COMMENT '首次已读时间',
  `payload_json` JSON NULL COMMENT '最小通知摘要，不嵌入私有知识正文',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`event_key`) COMMENT '同事件收件人幂等',
  KEY `idx_2` (`owner_id`, `read_at`, `created_at`) COMMENT '未读与历史通知'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='站内业务通知';

CREATE TABLE `knowledge_share_rule` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联knowledge_item 逻辑关联 knowledge_item.id',
  `friend_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联app_user 逻辑关联 app_user.id',
  `relation_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联friend_relation 逻辑关联 friend_relation.id',
  `relation_generation` INT UNSIGNED NOT NULL COMMENT 'allow需匹配当前关系代数；deny在取消排除前持续生效',
  `effect` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'allow指定好友；deny全体好友中的单人排除',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '规则版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`knowledge_id`, `friend_id`) COMMENT '每篇知识每位好友最多一条规则',
  KEY `idx_2` (`friend_id`, `effect`) COMMENT '被共享列表反查'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识指定授权与排除';

CREATE TABLE `resource_schedule` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户 逻辑关联 app_user.id',
  `name` VARCHAR(20) NOT NULL COMMENT '任务名称1至20字符',
  `resource_kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'resource/photo',
  `keywords` VARCHAR(500) NOT NULL COMMENT '采集主题或关键词',
  `weekdays_mask` TINYINT UNSIGNED NOT NULL COMMENT '周一bit0至周日bit6，1至127',
  `minute_of_day` SMALLINT UNSIGNED NOT NULL COMMENT '本地时间0至1439',
  `timezone` VARCHAR(40) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '固定Asia/Shanghai',
  `per_run_limit` SMALLINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '每次条数1至50；默认5',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active/paused/deleted',
  `next_run_at` DATETIME(3) NULL DEFAULT NULL COMMENT '下次触发UTC',
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '更新版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  `dedup_enabled` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1跳过本任务已收集资源；0允许跨批次重复；单批次始终去重',
  `summary_enabled` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '1请求AI摘要；须显式同意且预算可用；失败降级来源摘要',
  `topics_json` JSON NULL COMMENT '用户关注主题数组，与关键词共同构成筛选配置',
  PRIMARY KEY (`id`) COMMENT '主键',
  KEY `idx_1` (`owner_id`, `state`, `created_at`) COMMENT '个人任务列表',
  KEY `idx_2` (`state`, `next_run_at`) COMMENT '调度扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='个人定时资源任务';

CREATE TABLE `resource_schedule_source` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `schedule_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联resource_schedule 逻辑关联 resource_schedule.id',
  `source_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联content_source 逻辑关联 content_source.id',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`schedule_id`, `source_id`) COMMENT '任务来源不重复'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='定时任务来源选择';

CREATE TABLE `resource_run` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户 逻辑关联 app_user.id',
  `schedule_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联resource_schedule 逻辑关联 resource_schedule.id',
  `trigger_key` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '计划版本:时间槽或manual:幂等键',
  `schedule_version` INT UNSIGNED NOT NULL COMMENT '运行使用配置版本',
  `config_snapshot` JSON NOT NULL COMMENT '来源与配置快照',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'queued' COMMENT 'queued/running/success/partial/failed/cancelled',
  `attempt_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试计数',
  `started_at` DATETIME(3) NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` DATETIME(3) NULL DEFAULT NULL COMMENT '结束时间',
  `error_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '稳定错误码',
  `result_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '结果数量',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`schedule_id`, `trigger_key`) COMMENT '时间槽与手动触发幂等',
  KEY `idx_2` (`owner_id`, `created_at`, `id`) COMMENT '个人运行历史'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='定时采集运行';

CREATE TABLE `collected_resource` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `source_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联content_source 逻辑关联 content_source.id',
  `resource_kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'resource/photo',
  `origin_url` VARCHAR(2048) NOT NULL COMMENT '原始页面URL',
  `canonical_url_hash` BINARY(32) NOT NULL COMMENT '规范化URL SHA256',
  `title` VARCHAR(200) NOT NULL COMMENT '资源标题',
  `summary` VARCHAR(2000) NOT NULL COMMENT '合法摘要，不默认复制全文',
  `author` VARCHAR(200) NULL DEFAULT NULL COMMENT '作者署名',
  `published_at` DATETIME(3) NULL DEFAULT NULL COMMENT '来源发布时间',
  `thumbnail_url` VARCHAR(2048) NULL DEFAULT NULL COMMENT '允许展示的缩略图',
  `license_note` VARCHAR(1000) NULL DEFAULT NULL COMMENT '来源许可说明',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active/withdrawn',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  `tags_json` JSON NULL COMMENT '来源或审核分类标签，如AI/开源工具/风光/人像',
  `popularity_score` DECIMAL(16,4) NULL DEFAULT NULL COMMENT '来源内可比较的热度，未知为空；不可跨来源直接相加',
  `popularity_at` DATETIME(3) NULL DEFAULT NULL COMMENT '热度指标采样时间',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`canonical_url_hash`) COMMENT '资源URL全局去重'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='资源与摄影索引';

CREATE TABLE `resource_run_item` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `run_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联resource_run 逻辑关联 resource_run.id',
  `resource_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联collected_resource 逻辑关联 collected_resource.id',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  `ai_summary` TEXT NULL COMMENT '此用户此运行的AI摘要；不可覆盖公共来源摘要',
  `summary_ai_job_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '生成摘要任务，权限和预算同AI公共机制 逻辑关联 ai_job.id',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`run_id`, `resource_id`) COMMENT '单次结果去重'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='采集运行结果';

CREATE TABLE `knowledge_asset` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用生成32位小写十六进制ID',
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联knowledge_item 逻辑关联 knowledge_item.id',
  `asset_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关联media_asset 逻辑关联 media_asset.id',
  `sort_no` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '正文引用顺序',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC；ON UPDATE CURRENT_TIMESTAMP(3)',
  PRIMARY KEY (`id`) COMMENT '主键',
  UNIQUE KEY `uk_1` (`knowledge_id`, `asset_id`) COMMENT '知识图片引用唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识内图片引用';

-- 基线数据使用保留ID（0开头），业务随机ID生成器不得生成该保留段。
-- 未经业务评审不得修改已投产的基线ID，避免计划与内容主题关系失效。
START TRANSACTION;

INSERT INTO `admission_counter`
  (`id`, `scope_code`, `invited_limit`, `invited_used`)
VALUES
  ('00000000000000000000000000000001', 'trial', 20, 0);

INSERT INTO `learning_topic`
  (`id`, `scope_key`, `owner_id`, `name`, `normalized_name`, `state`)
VALUES
  ('00000000000000000000000000000011', 'system', NULL, 'AI', 'ai', 'active'),
  ('00000000000000000000000000000012', 'system', NULL, '计算机基础', '计算机基础', 'active');

COMMIT;

-- ============================================================================
-- 第二部分 V3.1 学段字段与基础内容
-- ============================================================================
-- ============================================================
-- 知行日课 V3.1 增量脚本：学段字段 + 基础素材(技术资讯/英语单词)
-- 适用：现有 letMindWander 库（V3.0 62 表结构，MySQL 5.7.25）
-- 原则：只 ALTER 加字段 + 插入种子数据，不重建任何表。
-- 可重复执行：ALTER 前判空，INSERT 用 ON DUPLICATE KEY / 固定ID幂等。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

-- ============================================================
-- 第一部分：结构变更（添加字段，不重建表）
-- ============================================================

-- 1. learning_content 增加「学段」字段：区分英语单词的小学/初中/高中类别
--    primary 小学 / junior 初中 / senior 高中；仅 content_type='word' 非空
SET @has_stage := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'learning_content' AND column_name = 'stage');
SET @ddl := IF(@has_stage = 0,
  'ALTER TABLE `learning_content` ADD COLUMN `stage` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT ''学段分类：primary 小学 / junior 初中 / senior 高中；仅 word 类型非空''',
  'DO 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 学段筛选索引（幂等）
SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'learning_content' AND index_name = 'idx_stage');
SET @ddl2 := IF(@has_idx = 0,
  'ALTER TABLE `learning_content` ADD KEY `idx_stage` (`content_type`, `stage`, `state`, `published_at`)',
  'DO 1');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- ============================================================
-- 第二部分：公开内容来源
-- ============================================================
INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`license_note`,`enabled`) VALUES
('7918934720e8180ff4d0f1610c0e4aab','人教版中小学英语词表','manual',NULL,'依据义务教育及普通高中英语课程标准公开词表整理，仅作学习用途',1) ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);
INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`license_note`,`enabled`) VALUES
('a7b04c72f9cc9be95bb5b862bf5e06cf','公开科技资讯','manual',NULL,'来源公开网络资讯，仅作学习引用，保留原文链接与原作者',1) ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);

-- ============================================================
-- 第三部分：系统主题（大模型 / AI应用）
-- ============================================================
INSERT INTO `learning_topic` (`id`,`scope_key`,`owner_id`,`name`,`normalized_name`,`state`) VALUES
('00000000000000000000000000000013','system',NULL,'大模型','大模型','active') ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);
INSERT INTO `learning_topic` (`id`,`scope_key`,`owner_id`,`name`,`normalized_name`,`state`) VALUES
('00000000000000000000000000000014','system',NULL,'AI应用','ai应用','active') ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);

-- ============================================================
-- 第四部分：技术资讯（learning_content + content_version + content_topic）
-- ============================================================
INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('2c1d3fd9a16f81b163906a1354fbfd31','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('3a8643b42ff2141736a9614979e0f40614d5c6e8401c1737df669b3101e57e5a'),UNHEX('cf5acb4f0fc74ff99b82a30dbd4f9dc0b40964b961ec6fb25ea126952f351daf'),'published','1a664297931d6f40cb66da78eff65860',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('1a664297931d6f40cb66da78eff65860','2c1d3fd9a16f81b163906a1354fbfd31',1,'人工智能大模型发展迈入新阶段','全球大模型市场进入多方制衡、多元竞争的新阶段，ChatGPT 市场份额首次跌破 50%。','2026 年以来大模型产业告别单一能力竞赛，形成「能力为王、成本制胜」的双重竞争范式。谷歌、Anthropic、OpenAI 交替领跑，中国开源模型以高性价比驱动市场重构。','intro',180,'https://www.digitalchina.gov.cn/2026/xwzx/szkx/202609/t20260903_5367434.htm','数字中国','2026-09-03 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('edd2e1aba6d6cf3f6212298d43bc14c8b265432eaa381920215ffddc9447ecbd'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('942f57387b4a024cca80991290ea9800','1a664297931d6f40cb66da78eff65860','00000000000000000000000000000011') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='1a664297931d6f40cb66da78eff65860' WHERE `id`='2c1d3fd9a16f81b163906a1354fbfd31';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('570ece9315d22c9890936f3f3a3f20eb','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('a6b8936d42a1313169d92d90ac490f7bba2d3676c13cb1471a47146b14a041a4'),UNHEX('6fef3fc17dceef394083773c352d93ece83cafff88f329c910661a5d6b2d511b'),'published','93de908ed8fd5cddef2ffd51db65e132',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('93de908ed8fd5cddef2ffd51db65e132','570ece9315d22c9890936f3f3a3f20eb',1,'腾讯混元 Hy4-preview 混合稀疏大模型','腾讯混元发布 Hy4-preview，770B 总参数、49B 激活参数，上下文窗口达 1M。','Hy4-preview 采用混合稀疏架构，软件工程任务评测表现提升，部分能力开源至腾讯云 TokenHub，主打高性能与长上下文。','intro',180,'https://blog.csdn.net/u014146389/article/details/164250211','CSDN AI日报','2026-09-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('dd41aa2c5fc02e090f249bacb10f9df0a27a28069ef37a5c5bfdb524ba3f22b4'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('cbd645568ddaab94b3921722bee9ea62','93de908ed8fd5cddef2ffd51db65e132','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='93de908ed8fd5cddef2ffd51db65e132' WHERE `id`='570ece9315d22c9890936f3f3a3f20eb';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('714c811d2d422d7863795ba6532faf50','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('9f29ed6a52d5a8445d26fced47bff291a90c1d25284ce5a1e779a03e57b98f2e'),UNHEX('6fef3fc17dceef394083773c352d93ece83cafff88f329c910661a5d6b2d511b'),'published','0f1a18762452881c450698174dd1b212',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('0f1a18762452881c450698174dd1b212','714c811d2d422d7863795ba6532faf50',1,'智谱 GLM-5.3 Flash 低成本推理','智谱 AI 上半年营收 9.54 亿元，GLM-5.3 Flash 主打低成本推理。','智谱 AI 已实现 10 万卡级国产芯片规模化推理，GLM-5.3 Flash 以低成本推理定位面向企业级应用。','intro',180,'https://blog.csdn.net/u014146389/article/details/164250211','CSDN AI日报','2026-09-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('34ffa073dfa5010b1f936bd84f9a20b47b65a3701e36af26f971d0222452f0e1'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('f03d2fdefae54e48f874b17434160317','0f1a18762452881c450698174dd1b212','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='0f1a18762452881c450698174dd1b212' WHERE `id`='714c811d2d422d7863795ba6532faf50';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('a49ca730fefb7dbaf85f39b5e8d8b3c8','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('66976d78c6706e186a2d5fcead837d0bef7c71c6f3c137a1810a187b4d403fb0'),UNHEX('6fef3fc17dceef394083773c352d93ece83cafff88f329c910661a5d6b2d511b'),'published','5c9b06126f0a1efed5750dee2c97d5a6',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('5c9b06126f0a1efed5750dee2c97d5a6','a49ca730fefb7dbaf85f39b5e8d8b3c8',1,'阿里通义千问 Agent Teams 多智能体协作','千问升级 Agent Teams 多智能体协作功能，接入 Wan3.0 视频生成模型。','一套工作流即可完成脚本、分镜、生成、剪辑的全链路 AI 内容创作，多智能体协作成为 AI 应用新趋势。','intro',180,'https://blog.csdn.net/u014146389/article/details/164250211','CSDN AI日报','2026-09-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('c8e4410360ce5f5733e8e3f2c3f2dc2d3da900d8f240a80c8063a1cc23e8e236'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('4c04c7fe0b679eccb1f9fcb810dcdc6f','5c9b06126f0a1efed5750dee2c97d5a6','00000000000000000000000000000014') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='5c9b06126f0a1efed5750dee2c97d5a6' WHERE `id`='a49ca730fefb7dbaf85f39b5e8d8b3c8';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('201cd5e95aad2e284de65e9b3932accd','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('8e0127aacee4ae07601312952f411aa136d48ba915912691b510de3597b91832'),UNHEX('be1c1cab0a1620abb1c03dec4b4d8f6c2dd25a777965ead772f4631febcfa733'),'published','a448241132a46d4a0578405b907941a2',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('a448241132a46d4a0578405b907941a2','201cd5e95aad2e284de65e9b3932accd',1,'OpenAI GPT-5.5 Instant 发布','GPT-5.5 Instant 幻觉率降低 52%，推理速度大幅提升。','新模型标志着大模型进入「快准稳」新阶段，在推理效率与可靠性之间取得新平衡。','intro',180,'https://blog.csdn.net/enheng1238/article/details/164294388','CSDN AI日报','2026-09-02 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('7de301bfe6a9e1d31b4f6f86b93ba630a9c5bf7972866d216ea34e5ad05f67cd'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('d8ba929d2802af4a3e0c1e39077a8ae8','a448241132a46d4a0578405b907941a2','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='a448241132a46d4a0578405b907941a2' WHERE `id`='201cd5e95aad2e284de65e9b3932accd';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('89af0b1539f35b6a3c4e7baa51a945fd','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('081cfd6daf3536d1b2a3f9c7dbff2befc1ff93bca910c0b93042516225780f2c'),UNHEX('71c9804d6f0725e8e320c66c91b2ee2c7d4685fd4edb837f5af83da5893aa360'),'published','0f6b9285296bd86007c56e71f95c6d61',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('0f6b9285296bd86007c56e71f95c6d61','89af0b1539f35b6a3c4e7baa51a945fd',1,'DeepSeek V4 开源，推理价格创新低','DeepSeek V4 以超低价格开源，性能逼近顶级闭源模型。','输入价格低至约 0.14 美元/百万 token，引发行业性价比竞争，推动开源模型加速普及。','intro',180,'https://devpress.csdn.net/v1/article/detail/163659168','CSDN AI日报','2026-08-15 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('6aa3069c32e459e9f57c81f84f2bfda38175d54c3e92324bdc47901d5c46ce80'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('23657dc7e2cd5dfcf60d66238327b62f','0f6b9285296bd86007c56e71f95c6d61','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='0f6b9285296bd86007c56e71f95c6d61' WHERE `id`='89af0b1539f35b6a3c4e7baa51a945fd';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('125dffbdcd7e8d16d14f74f2f7be30fb','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('1004645f5fcac3ab34715ab05d2133bd881feaae8f7431dc4cbd799d9ac17673'),UNHEX('cf5acb4f0fc74ff99b82a30dbd4f9dc0b40964b961ec6fb25ea126952f351daf'),'published','a8d0b0f3e5fe6352690026dc78d5312c',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('a8d0b0f3e5fe6352690026dc78d5312c','125dffbdcd7e8d16d14f74f2f7be30fb',1,'美团 LongCat-2.0 万亿参数大模型','美团开源 LongCat-2.0（1.6 万亿参数），全程依托超 5 万张国产算力卡完成训练与推理。','LongCat-2.0 是业界首个纯国产算力万亿级模型，标志国产算力生态迈入新阶段。','intro',180,'https://www.digitalchina.gov.cn/2026/xwzx/szkx/202609/t20260903_5367434.htm','数字中国','2026-06-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('3830e0dacf24e86aa8b1be810d79abaeb5563857ad5e933479f78a00d405b67e'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('470af350e648f4b81cc0b681733ed7e1','a8d0b0f3e5fe6352690026dc78d5312c','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='a8d0b0f3e5fe6352690026dc78d5312c' WHERE `id`='125dffbdcd7e8d16d14f74f2f7be30fb';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('57e72b0e53b66a8b72e11e7c5650757e','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('d1907d899e755fa2d5302a1f601632ec79613a5748e633e9cc4ae004c1bf471c'),UNHEX('cf5acb4f0fc74ff99b82a30dbd4f9dc0b40964b961ec6fb25ea126952f351daf'),'published','b4ec0ddf5986b325cfbbc88632f39a42',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('b4ec0ddf5986b325cfbbc88632f39a42','57e72b0e53b66a8b72e11e7c5650757e',1,'华为昇腾 384 超节点算力集群','昇腾 384 超节点提供 300PFLOPS 密集 BF16 算力，已用于训练盘古 Ultra MoE 大模型。','华为昇腾 384 超节点性能接近英伟达 GB200 NVL72 两倍，支撑国产大模型训练。','advanced',180,'https://www.digitalchina.gov.cn/2026/xwzx/szkx/202609/t20260903_5367434.htm','数字中国','2026-06-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('626dd1c299a2213780c9f9c1e5f5b5c1460ee14f1648d264034624972b25296c'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('17d90ecfccd183bd16544753d8df39b2','b4ec0ddf5986b325cfbbc88632f39a42','00000000000000000000000000000014') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='b4ec0ddf5986b325cfbbc88632f39a42' WHERE `id`='57e72b0e53b66a8b72e11e7c5650757e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('29f26bb95462ba13145830c1018ea0a2','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('4b109739bda4e68060523e0f013961bec57b88a42d76c8de1f157b3bd04637d8'),UNHEX('f3fa42d5ae9a4d3dc4dc5448debcd0e9071749bd47a99d3c02e01c4d4b26e0f6'),'published','109b48ce1815f6892c6f4ab41eebc6ea',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('109b48ce1815f6892c6f4ab41eebc6ea','29f26bb95462ba13145830c1018ea0a2',1,'2026 年中国 AI 发展趋势前瞻','AI 企业超 6000 家，核心产业规模预计突破 1.2 万亿元。','大模型从「拼规模」转向「拼密度」，稀疏注意力机制成为提升推理效率的重要技术路径。','intro',180,'https://www.news.cn/20260128/3b2f11906fd74ca397fef9996c805a60/c.html','新华社','2026-01-28 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('86dd0e234e76ee4ef61ac25756e285c5229053e3d64486d635c9be0b1438a8f2'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('e696d6a5f635cbd4615e0b20630734e3','109b48ce1815f6892c6f4ab41eebc6ea','00000000000000000000000000000011') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='109b48ce1815f6892c6f4ab41eebc6ea' WHERE `id`='29f26bb95462ba13145830c1018ea0a2';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`origin_url_hash`,`state`,`published_version_id`,`published_at`,`row_version`) VALUES
('96c4f701034514bda87ae14c53985072','tech','a7b04c72f9cc9be95bb5b862bf5e06cf',UNHEX('e99f9d8c42e06f1351e9f1520c95ab711f55f7624f9966853f16df070c8f894a'),UNHEX('cf5acb4f0fc74ff99b82a30dbd4f9dc0b40964b961ec6fb25ea126952f351daf'),'published','33750d40260baf43a15e99023c23a548',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_url`,`origin_author`,`origin_published_at`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('33750d40260baf43a15e99023c23a548','96c4f701034514bda87ae14c53985072',1,'Claude Sonnet 5 发布','Anthropic 推出新一代中端主力模型 Claude Sonnet 5，主打代理能力。','官方称其为迄今「最具代理能力」的 Sonnet 模型，强化长文档处理与多工具并行调用。','intro',180,'https://www.digitalchina.gov.cn/2026/xwzx/szkx/202609/t20260903_5367434.htm','数字中国','2026-07-01 00:00:00','来源公开网络资讯，仅作学习引用',UNHEX('275a1b3afd5a6562238491b97818c6b3413d2e21afa3aa7f9e4f6b6fb567f729'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`) VALUES
('9fb52eabaec3a0c3d1d592fe87e996b6','33750d40260baf43a15e99023c23a548','00000000000000000000000000000013') ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

UPDATE `learning_content` SET `current_version_id`='33750d40260baf43a15e99023c23a548' WHERE `id`='96c4f701034514bda87ae14c53985072';

-- ============================================================
-- 第五部分：英语单词（小学/初中/高中，learning_content.stage 区分类别）
-- ============================================================
INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('32d0e4b09fa3ddde7dd1fa1be5a45e07','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('5876012d5c48963d658d85985f92c1516dca2a448c27d230752b2b5add4f4f08'),UNHEX('3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b'),'published','primary','aa1a0da8a7f040cc6f3f17e4ccf1a6ed',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('aa1a0da8a7f040cc6f3f17e4ccf1a6ed','32d0e4b09fa3ddde7dd1fa1be5a45e07',1,'apple','apple','/ˈæpl/','苹果','I eat an apple every day.','我每天吃一个苹果。','intro',15,'公开英语词表，仅作学习用途',UNHEX('3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('1de06f87ef1148b6f771b9caaa4ee657','aa1a0da8a7f040cc6f3f17e4ccf1a6ed','noun','苹果',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('0dc0c8fef86f745f7e04f8df0f1875f6','1de06f87ef1148b6f771b9caaa4ee657','I eat an apple every day.','我每天吃一个苹果。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='aa1a0da8a7f040cc6f3f17e4ccf1a6ed' WHERE `id`='32d0e4b09fa3ddde7dd1fa1be5a45e07';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('d12d2a85ea7992417f8af5e102ef62c2','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('cb5cd6845de3c596636757d4b9b24384573dcfbd4e9fe1f02cbe7b7bcf74d5bf'),UNHEX('92719fe0cf8cd51592af31ee8a5736d79f7273777fa3f7b70bfe993a4cd32180'),'published','primary','a235ac3b22eca4290ff217db4dd760c5',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('a235ac3b22eca4290ff217db4dd760c5','d12d2a85ea7992417f8af5e102ef62c2',1,'book','book','/bʊk/','书','This book is interesting.','这本书很有趣。','intro',15,'公开英语词表，仅作学习用途',UNHEX('92719fe0cf8cd51592af31ee8a5736d79f7273777fa3f7b70bfe993a4cd32180'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('e87000337cd4531009b16038ca486632','a235ac3b22eca4290ff217db4dd760c5','noun','书',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('3be7f1498040a2c1f0d3183280ef3baa','e87000337cd4531009b16038ca486632','This book is interesting.','这本书很有趣。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='a235ac3b22eca4290ff217db4dd760c5' WHERE `id`='d12d2a85ea7992417f8af5e102ef62c2';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('eac2bf0ddae12ce6dcd2e4bfe555e775','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('754b4ee0bf1b6d8475b3778fd4dfade5c176d051b12a4575c76a73b8b3ad1a00'),UNHEX('77af778b51abd4a3c51c5ddd97204a9c3ae614ebccb75a606c3b6865aed6744e'),'published','primary','5178ba7f288a4248614000cfde2559d7',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('5178ba7f288a4248614000cfde2559d7','eac2bf0ddae12ce6dcd2e4bfe555e775',1,'cat','cat','/kæt/','猫','The cat is sleeping.','猫在睡觉。','intro',15,'公开英语词表，仅作学习用途',UNHEX('77af778b51abd4a3c51c5ddd97204a9c3ae614ebccb75a606c3b6865aed6744e'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('79c594eb23a951ddc480186673e15d58','5178ba7f288a4248614000cfde2559d7','noun','猫',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('13d27ee71f99bbe52f70965f3e038857','79c594eb23a951ddc480186673e15d58','The cat is sleeping.','猫在睡觉。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='5178ba7f288a4248614000cfde2559d7' WHERE `id`='eac2bf0ddae12ce6dcd2e4bfe555e775';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('08241716d13bbdc2bde15db31b1db89e','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('6b28e1144d9d722cb1399534b3877c47aae72e0557faebe6e900c5ec3507d55e'),UNHEX('cd6357efdd966de8c0cb2f876cc89ec74ce35f0968e11743987084bd42fb8944'),'published','primary','7f99e9d47af443e20ae351d587ec01dd',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('7f99e9d47af443e20ae351d587ec01dd','08241716d13bbdc2bde15db31b1db89e',1,'dog','dog','/dɒɡ/','狗','My dog is very friendly.','我的狗很友好。','intro',15,'公开英语词表，仅作学习用途',UNHEX('cd6357efdd966de8c0cb2f876cc89ec74ce35f0968e11743987084bd42fb8944'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('722f417bfba31566f59e9387c75ad667','7f99e9d47af443e20ae351d587ec01dd','noun','狗',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('c61be795e7d4d2955bafd312194fa7ea','722f417bfba31566f59e9387c75ad667','My dog is very friendly.','我的狗很友好。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='7f99e9d47af443e20ae351d587ec01dd' WHERE `id`='08241716d13bbdc2bde15db31b1db89e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('6e5ea74a2362d12cf0453d973f70d05e','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('82b39b1b0df9f437a5ccc3662d61f1ae49fdc10e744ddefdf5ed910983f59624'),UNHEX('770e607624d689265ca6c44884d0807d9b054d23c473c106c72be9de08b7376c'),'published','primary','e5f182fdc9a55fb6853705f449849b91',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('e5f182fdc9a55fb6853705f449849b91','6e5ea74a2362d12cf0453d973f70d05e',1,'good','good','/ɡʊd/','好的','You did a good job.','你做得很好。','intro',15,'公开英语词表，仅作学习用途',UNHEX('770e607624d689265ca6c44884d0807d9b054d23c473c106c72be9de08b7376c'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('122c47ceb4de0f05d4a3832b85190eb3','e5f182fdc9a55fb6853705f449849b91','adjective','好的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('07e35b9c9cbbad50b62dc5545c8f76b6','122c47ceb4de0f05d4a3832b85190eb3','You did a good job.','你做得很好。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='e5f182fdc9a55fb6853705f449849b91' WHERE `id`='6e5ea74a2362d12cf0453d973f70d05e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('623cfdf90b2f81f1e835ee299a0c7f5e','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('bb9d5fe5b77e1f343b387faefe0aaf4ee105fee7238258b8ba85aaaf84c66fe0'),UNHEX('acba25512100f80b56fc3ccd14c65be55d94800cda77585c5f41a887e398f9be'),'published','primary','72e72bc4ee4e71679977f23deb91303f',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('72e72bc4ee4e71679977f23deb91303f','623cfdf90b2f81f1e835ee299a0c7f5e',1,'run','run','/rʌn/','跑','I run in the morning.','我早上跑步。','intro',15,'公开英语词表，仅作学习用途',UNHEX('acba25512100f80b56fc3ccd14c65be55d94800cda77585c5f41a887e398f9be'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('28cc009d8b0e34adb6d08e1aa81cce52','72e72bc4ee4e71679977f23deb91303f','verb','跑',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('95e105d0b720e4fb7a3120e76480e1bc','28cc009d8b0e34adb6d08e1aa81cce52','I run in the morning.','我早上跑步。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='72e72bc4ee4e71679977f23deb91303f' WHERE `id`='623cfdf90b2f81f1e835ee299a0c7f5e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('795ab453e5660467f28e3aa2cae4287c','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('0e9cbde8075806789cdbcb3209a88d93788dcd797548541cf2864615bcd873cb'),UNHEX('2a21fe6d592a19b7de898b50eb53c429608de1a66f3e9f62da19714a770553d1'),'published','primary','a869eebd4c03d2eb8f3d2cfa30fc72ce',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('a869eebd4c03d2eb8f3d2cfa30fc72ce','795ab453e5660467f28e3aa2cae4287c',1,'big','big','/bɪɡ/','大的','That is a big tree.','那是一棵大树。','intro',15,'公开英语词表，仅作学习用途',UNHEX('2a21fe6d592a19b7de898b50eb53c429608de1a66f3e9f62da19714a770553d1'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('3cee626ae05a17ca080f8904fac0ddb2','a869eebd4c03d2eb8f3d2cfa30fc72ce','adjective','大的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('f62616fc16aeef65647a7dcd69ac3860','3cee626ae05a17ca080f8904fac0ddb2','That is a big tree.','那是一棵大树。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='a869eebd4c03d2eb8f3d2cfa30fc72ce' WHERE `id`='795ab453e5660467f28e3aa2cae4287c';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('d7e06b7e0312565f2ce3973b6e1621d2','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('7ff42dcc17583a1e150469036bb3654237d89a80d51e6427b62e59704946900e'),UNHEX('489f719cadf919094ddb38e7654de153ac33c02febb5de91e5345cbe372cf4a0'),'published','primary','c257d6e568516935ecc31b428e84c117',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('c257d6e568516935ecc31b428e84c117','d7e06b7e0312565f2ce3973b6e1621d2',1,'happy','happy','/ˈhæpi/','快乐的','She looks happy today.','她今天看起来很开心。','intro',15,'公开英语词表，仅作学习用途',UNHEX('489f719cadf919094ddb38e7654de153ac33c02febb5de91e5345cbe372cf4a0'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('75a142d1768006bf70b2f8ab8adaed20','c257d6e568516935ecc31b428e84c117','adjective','快乐的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('0b1fc7fe1f425e7481306221f82c0aa4','75a142d1768006bf70b2f8ab8adaed20','She looks happy today.','她今天看起来很开心。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='c257d6e568516935ecc31b428e84c117' WHERE `id`='d7e06b7e0312565f2ce3973b6e1621d2';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('ac7d6602011575e040508df8a08e0f14','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('ff237c9f5d9d9f757571612f93faf9ea43cd24c24784e1ee53bfded561c1a847'),UNHEX('d64debd942d7dc26a851231583b1721f43ea936fa41932b6dad7556e5f8cd24a'),'published','primary','40448c65895a420ca0f93dbee362bbc3',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('40448c65895a420ca0f93dbee362bbc3','ac7d6602011575e040508df8a08e0f14',1,'school','school','/skuːl/','学校','I go to school by bus.','我坐公交车去学校。','intro',15,'公开英语词表，仅作学习用途',UNHEX('d64debd942d7dc26a851231583b1721f43ea936fa41932b6dad7556e5f8cd24a'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('3ce818f09995b4098d1f9ee7e46acd61','40448c65895a420ca0f93dbee362bbc3','noun','学校',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('56926cfcfa5fb4ac3d780a41b6198d64','3ce818f09995b4098d1f9ee7e46acd61','I go to school by bus.','我坐公交车去学校。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='40448c65895a420ca0f93dbee362bbc3' WHERE `id`='ac7d6602011575e040508df8a08e0f14';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('f590aed1f648037e87e42442b67b2219','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('cc09e82f3ce5e7425857ca75474e62982422449a6fdc92e755c6b7148518d692'),UNHEX('0f4168490e38b8447e11ba4bd656aa11b925bd22af30bac464bc153fdb608501'),'published','primary','32a78a687e717e45e116f3ade4d420aa',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('32a78a687e717e45e116f3ade4d420aa','f590aed1f648037e87e42442b67b2219',1,'water','water','/ˈwɔːtə/','水','Please drink some water.','请喝点水。','intro',15,'公开英语词表，仅作学习用途',UNHEX('0f4168490e38b8447e11ba4bd656aa11b925bd22af30bac464bc153fdb608501'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('64e3baded7e98bf43a07c5f38ae314bc','32a78a687e717e45e116f3ade4d420aa','noun','水',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('3c9b7d6b173df1293ebceebf8778e0ee','64e3baded7e98bf43a07c5f38ae314bc','Please drink some water.','请喝点水。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='32a78a687e717e45e116f3ade4d420aa' WHERE `id`='f590aed1f648037e87e42442b67b2219';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('52666718d87f008288ecd24b277be48f','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('765c737b059f5771e1e00e4ca05ec3f12e35ad6485ad2d88fc39a9ab0c169d76'),UNHEX('1c6b6b9e01275af3ac768463d2c751b5c0ee7324ba31723ba694ba4ad48eaf3f'),'published','junior','6e0d2352f815c0254d83a747068e6a26',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('6e0d2352f815c0254d83a747068e6a26','52666718d87f008288ecd24b277be48f',1,'achieve','achieve','/əˈtʃiːv/','实现，达到','She worked hard to achieve her goal.','她努力实现自己的目标。','intro',15,'公开英语词表，仅作学习用途',UNHEX('1c6b6b9e01275af3ac768463d2c751b5c0ee7324ba31723ba694ba4ad48eaf3f'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('6e239951c928e19a0cd2d06f4f64cdeb','6e0d2352f815c0254d83a747068e6a26','verb','实现，达到',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('6dc323bb9bf5406f472ce03a8b6a739c','6e239951c928e19a0cd2d06f4f64cdeb','She worked hard to achieve her goal.','她努力实现自己的目标。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='6e0d2352f815c0254d83a747068e6a26' WHERE `id`='52666718d87f008288ecd24b277be48f';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('0d9a35db20c602a20f4109cd309d7d19','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('b94df3527d7c77d79972a3320a83b7db48223b5aa6c2a99afb79383645c776cc'),UNHEX('ba5285161ba6eed0085fb13784ce5c92f70ebc268b94fd66aa1d68a32884204d'),'published','junior','173a593894a862cfc958fc38f06d45a8',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('173a593894a862cfc958fc38f06d45a8','0d9a35db20c602a20f4109cd309d7d19',1,'environment','environment','/ɪnˈvaɪrənmənt/','环境','We should protect the environment.','我们应该保护环境。','intro',15,'公开英语词表，仅作学习用途',UNHEX('ba5285161ba6eed0085fb13784ce5c92f70ebc268b94fd66aa1d68a32884204d'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('cd21e8876cf198976d72648f1910e10f','173a593894a862cfc958fc38f06d45a8','noun','环境',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('1441d24fb4d8c835ccb73ea6ad9ced7d','cd21e8876cf198976d72648f1910e10f','We should protect the environment.','我们应该保护环境。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='173a593894a862cfc958fc38f06d45a8' WHERE `id`='0d9a35db20c602a20f4109cd309d7d19';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('be95d376af2614e73fc0ec0c2b565fd0','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('07395af3bc31da8fb4491b3a4594259b879dad28f089f53b584317066f124eb5'),UNHEX('f1947f79fdfb8046150959ca09cdd05cb53672ad4c0f49a87bbc7cddf5c91293'),'published','junior','b52aa56a0362aa852f636f89eeda6585',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('b52aa56a0362aa852f636f89eeda6585','be95d376af2614e73fc0ec0c2b565fd0',1,'culture','culture','/ˈkʌltʃə/','文化','China has a long history and rich culture.','中国历史悠久、文化丰富。','intro',15,'公开英语词表，仅作学习用途',UNHEX('f1947f79fdfb8046150959ca09cdd05cb53672ad4c0f49a87bbc7cddf5c91293'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('a679f2700abbb69b61f096e336740319','b52aa56a0362aa852f636f89eeda6585','noun','文化',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('6449b7520dace793633408cf42a792d7','a679f2700abbb69b61f096e336740319','China has a long history and rich culture.','中国历史悠久、文化丰富。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='b52aa56a0362aa852f636f89eeda6585' WHERE `id`='be95d376af2614e73fc0ec0c2b565fd0';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('dd4dced045f8db3f96fe0a6d92fe7bd9','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('6d2a06cb59ef9d570acefe6af57030fc115b8dd0628dd77e65e8840168852240'),UNHEX('c52028f34e378d1e07b4d5b8d10e4860e99cd3802218acf9391db8ea64fd2899'),'published','junior','169d6300ccb4ecb737490615492eb6e5',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('169d6300ccb4ecb737490615492eb6e5','dd4dced045f8db3f96fe0a6d92fe7bd9',1,'describe','describe','/dɪˈskraɪb/','描述','Can you describe the picture?','你能描述一下这张图吗？','intro',15,'公开英语词表，仅作学习用途',UNHEX('c52028f34e378d1e07b4d5b8d10e4860e99cd3802218acf9391db8ea64fd2899'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('a154d437e31ab09d5c15e809903cf196','169d6300ccb4ecb737490615492eb6e5','verb','描述',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('cee5a38ad569fd002267b6459059b4d6','a154d437e31ab09d5c15e809903cf196','Can you describe the picture?','你能描述一下这张图吗？',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='169d6300ccb4ecb737490615492eb6e5' WHERE `id`='dd4dced045f8db3f96fe0a6d92fe7bd9';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('d9d151ad6d139ac8b43e965212cd6c50','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('2815d193c02c77ba6cb0c74ef8cf0c9482d7821d1ad054ae47a9096b71cac66d'),UNHEX('04b447783afe5bfd4b48e8db067137319526a98e029c37b2559154ceccc1067e'),'published','junior','4d489316ac707ca1aabb187292617cc4',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('4d489316ac707ca1aabb187292617cc4','d9d151ad6d139ac8b43e965212cd6c50',1,'opportunity','opportunity','/ˌɒpəˈtjuːnəti/','机会','This is a good opportunity to learn.','这是一个学习的好机会。','intro',15,'公开英语词表，仅作学习用途',UNHEX('04b447783afe5bfd4b48e8db067137319526a98e029c37b2559154ceccc1067e'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('c21dfd5a7601918e5705b6e85ae01549','4d489316ac707ca1aabb187292617cc4','noun','机会',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('1544725cfb68634b0588b28dc5ecf8a6','c21dfd5a7601918e5705b6e85ae01549','This is a good opportunity to learn.','这是一个学习的好机会。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='4d489316ac707ca1aabb187292617cc4' WHERE `id`='d9d151ad6d139ac8b43e965212cd6c50';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('4740a422f6e3e919bbb39a8ec470eae8','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('5a3ca37130966d2cd337b06943af95fc10f206d4f2de5493040790361bdfabc7'),UNHEX('e0f895872d65b2528feec97350a3a212b3d4ab88748e25d022a34641d338216b'),'published','junior','6c3a613c186c4bc65b8bc0e5be6058f8',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('6c3a613c186c4bc65b8bc0e5be6058f8','4740a422f6e3e919bbb39a8ec470eae8',1,'knowledge','knowledge','/ˈnɒlɪdʒ/','知识','Knowledge is power.','知识就是力量。','intro',15,'公开英语词表，仅作学习用途',UNHEX('e0f895872d65b2528feec97350a3a212b3d4ab88748e25d022a34641d338216b'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('4f85fee403eec4e4c7162b2650bec637','6c3a613c186c4bc65b8bc0e5be6058f8','noun','知识',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('dd1591c9b350b150e3085803e13d83c3','4f85fee403eec4e4c7162b2650bec637','Knowledge is power.','知识就是力量。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='6c3a613c186c4bc65b8bc0e5be6058f8' WHERE `id`='4740a422f6e3e919bbb39a8ec470eae8';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('5e31799f2d71332bbeb66994e9f36a31','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('58825f59269d9eeb20b5bf3ca7ee8614bc144520e170c6bcd9f8f9c14ddbf357'),UNHEX('c9195f0946897bd7094ab4dc0ffbfade1a43544fed9ea93a5b448971952feed3'),'published','junior','69ecfc204e11cd595f47877a29a176e2',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('69ecfc204e11cd595f47877a29a176e2','5e31799f2d71332bbeb66994e9f36a31',1,'responsible','responsible','/rɪˈspɒnsəbl/','负责任的','You should be responsible for your actions.','你应该对自己的行为负责。','intro',15,'公开英语词表，仅作学习用途',UNHEX('c9195f0946897bd7094ab4dc0ffbfade1a43544fed9ea93a5b448971952feed3'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('ab250271f26a9e73d3e6c2f8811f1dee','69ecfc204e11cd595f47877a29a176e2','adjective','负责任的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('909cad88d4c9cbd8fe61faa060843d74','ab250271f26a9e73d3e6c2f8811f1dee','You should be responsible for your actions.','你应该对自己的行为负责。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='69ecfc204e11cd595f47877a29a176e2' WHERE `id`='5e31799f2d71332bbeb66994e9f36a31';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('9de617f01bc402443176cfd7c7b6625f','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('caed91dde26ef3e91d4a832c27cd1ec281800b3d67480be1c0fe36117f0f12a1'),UNHEX('f354ee99e2bc863ce19d80b843353476394ebc3530a51c9290d629065bacc3b3'),'published','junior','c51245276c449b90ae7e79fd7ef8505a',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('c51245276c449b90ae7e79fd7ef8505a','9de617f01bc402443176cfd7c7b6625f',1,'community','community','/kəˈmjuːnəti/','社区','We live in a friendly community.','我们住在一个友好的社区。','intro',15,'公开英语词表，仅作学习用途',UNHEX('f354ee99e2bc863ce19d80b843353476394ebc3530a51c9290d629065bacc3b3'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('5fc9c9bd5754fcb7bb259062e12b131d','c51245276c449b90ae7e79fd7ef8505a','noun','社区',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('9d8d1cc61db62d3be2aa11c484e2fe03','5fc9c9bd5754fcb7bb259062e12b131d','We live in a friendly community.','我们住在一个友好的社区。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='c51245276c449b90ae7e79fd7ef8505a' WHERE `id`='9de617f01bc402443176cfd7c7b6625f';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('2db9c6fe4957ceda70c6b7b207bec803','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('4aecaaf831dae86b94def0a212e81a5c14948d947f6862d6023636ee2dd798f2'),UNHEX('53e5e7c5a884893739c4cf64528572aeb846f8e4ca1986188287475c65900fab'),'published','junior','1f5b44de6df63503c80766707c162cb6',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('1f5b44de6df63503c80766707c162cb6','2db9c6fe4957ceda70c6b7b207bec803',1,'experience','experience','/ɪkˈspɪəriəns/','经验，经历','He has much teaching experience.','他有丰富的教学经验。','intro',15,'公开英语词表，仅作学习用途',UNHEX('53e5e7c5a884893739c4cf64528572aeb846f8e4ca1986188287475c65900fab'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('316f46dd9206801b730bef209c4d6479','1f5b44de6df63503c80766707c162cb6','noun','经验，经历',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('d0a10b07a7f7fdead34a0dfa0a743568','316f46dd9206801b730bef209c4d6479','He has much teaching experience.','他有丰富的教学经验。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='1f5b44de6df63503c80766707c162cb6' WHERE `id`='2db9c6fe4957ceda70c6b7b207bec803';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('99aeae313d7f3a644e8dfe878d6d12d2','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('d4fe73cbbd8780bab51a60589a71b81f5dfc6990feb2d9b83149ea0ec2518bf4'),UNHEX('2b35ed6944dd2e8f7462b14096e8969711280dffe1457a680c885a95127e426c'),'published','junior','4fd06461276506369238f259f37a5d01',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('4fd06461276506369238f259f37a5d01','99aeae313d7f3a644e8dfe878d6d12d2',1,'improve','improve','/ɪmˈpruːv/','改善，提高','I want to improve my English.','我想提高我的英语。','intro',15,'公开英语词表，仅作学习用途',UNHEX('2b35ed6944dd2e8f7462b14096e8969711280dffe1457a680c885a95127e426c'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('04503b3e83563bb2ac6557ded84fe13d','4fd06461276506369238f259f37a5d01','verb','改善，提高',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('fa407e42c2b1e958faa8fe455d2eee54','04503b3e83563bb2ac6557ded84fe13d','I want to improve my English.','我想提高我的英语。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='4fd06461276506369238f259f37a5d01' WHERE `id`='99aeae313d7f3a644e8dfe878d6d12d2';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('2f7544ea697c478b6c6545585faa3a7c','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('78c072a9b7a8f3540b0a430448aefdc0abf0980368d7b183100600ebdada2ed6'),UNHEX('3754c68d4b05de4538e0edf5d9fc8bef08397314b5e683a6fecbc9e319bbe3c7'),'published','senior','216cfa804177e1391ac02a6d418d985f',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('216cfa804177e1391ac02a6d418d985f','2f7544ea697c478b6c6545585faa3a7c',1,'phenomenon','phenomenon','/fəˈnɒmɪnən/','现象','This is a common social phenomenon.','这是一种常见的社会现象。','intro',15,'公开英语词表，仅作学习用途',UNHEX('3754c68d4b05de4538e0edf5d9fc8bef08397314b5e683a6fecbc9e319bbe3c7'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('8b47669bdf94823146d1b704087009cf','216cfa804177e1391ac02a6d418d985f','noun','现象',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('d784e8ffd0a0b8f7d6075f51ae0d313d','8b47669bdf94823146d1b704087009cf','This is a common social phenomenon.','这是一种常见的社会现象。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='216cfa804177e1391ac02a6d418d985f' WHERE `id`='2f7544ea697c478b6c6545585faa3a7c';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('3b182632b6512891954c33dc633752bc','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('d44ff8b2b35b74efb1644dc13d44ecc26f09f39a2a249df403ae508172f072c8'),UNHEX('e61db885bd6e7f403a7bc1d80577ecb7e31b18121b69534a04e986cd4d215fa0'),'published','senior','fd056c40ed85c86721430a6b8b458fb1',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('fd056c40ed85c86721430a6b8b458fb1','3b182632b6512891954c33dc633752bc',1,'significant','significant','/sɪɡˈnɪfɪkənt/','重要的，显著的','There is a significant difference between them.','它们之间有显著差异。','intro',15,'公开英语词表，仅作学习用途',UNHEX('e61db885bd6e7f403a7bc1d80577ecb7e31b18121b69534a04e986cd4d215fa0'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('b73b18454a699151473b87b07b4f0468','fd056c40ed85c86721430a6b8b458fb1','adjective','重要的，显著的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('0085f69c4424002759fbe1352607a609','b73b18454a699151473b87b07b4f0468','There is a significant difference between them.','它们之间有显著差异。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='fd056c40ed85c86721430a6b8b458fb1' WHERE `id`='3b182632b6512891954c33dc633752bc';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('85c46187cc43ae2e65358fb67e95ef32','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('563a798f0eb6c2cc9aac5510d31b58b466b80f94499a1796902d9dfae955af84'),UNHEX('74425421116546fd8872363f511450b62a1853310fedff6c283f55569bcc1ffe'),'published','senior','017c247893cfc8d85cf29af55c6eb4e2',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('017c247893cfc8d85cf29af55c6eb4e2','85c46187cc43ae2e65358fb67e95ef32',1,'perspective','perspective','/pəˈspektɪv/','观点，视角','We should look at the problem from a different perspective.','我们应该从不同角度看问题。','intro',15,'公开英语词表，仅作学习用途',UNHEX('74425421116546fd8872363f511450b62a1853310fedff6c283f55569bcc1ffe'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('f48c020ca3214905e65ac9b852d9aaa5','017c247893cfc8d85cf29af55c6eb4e2','noun','观点，视角',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('e10fa4d5c6ecedccda8e1b8e2752469e','f48c020ca3214905e65ac9b852d9aaa5','We should look at the problem from a different perspective.','我们应该从不同角度看问题。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='017c247893cfc8d85cf29af55c6eb4e2' WHERE `id`='85c46187cc43ae2e65358fb67e95ef32';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('4a74a74570d506a5e5d9b5461e923c9b','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('b37892bcf239081533f89232552f56e48d88af381b7d36f82b88a1a4c28abf9a'),UNHEX('6611032577e5a7b3a146e7b2111117b71a2a7b0fa4ef2861353816730a4f4a02'),'published','senior','f99c89521fb0b9b3130ad4534299370c',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('f99c89521fb0b9b3130ad4534299370c','4a74a74570d506a5e5d9b5461e923c9b',1,'innovation','innovation','/ˌɪnəˈveɪʃn/','创新','Innovation drives the development of society.','创新推动社会发展。','intro',15,'公开英语词表，仅作学习用途',UNHEX('6611032577e5a7b3a146e7b2111117b71a2a7b0fa4ef2861353816730a4f4a02'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('239eb8507d65d4f85c68c6dece0c4630','f99c89521fb0b9b3130ad4534299370c','noun','创新',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('27b0406c82c54c5430e528d26b2fd9c7','239eb8507d65d4f85c68c6dece0c4630','Innovation drives the development of society.','创新推动社会发展。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='f99c89521fb0b9b3130ad4534299370c' WHERE `id`='4a74a74570d506a5e5d9b5461e923c9b';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('2311891a306fe49e5398fda56cae6334','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('abb684ab8e829c242b9cc771e8c7ddda44d0d829a2e6fefb8866d0a6555f11b7'),UNHEX('4994ad9e4f40b39e4773dd86c85e61ce997bb4ae85a39d4a373332a3035a96be'),'published','senior','f0c8dc975bc5d2e1274da64d71693454',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('f0c8dc975bc5d2e1274da64d71693454','2311891a306fe49e5398fda56cae6334',1,'comprehensive','comprehensive','/ˌkɒmprɪˈhensɪv/','全面的','The report gives a comprehensive analysis.','这份报告给出了全面的分析。','intro',15,'公开英语词表，仅作学习用途',UNHEX('4994ad9e4f40b39e4773dd86c85e61ce997bb4ae85a39d4a373332a3035a96be'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('9c7a9b87867c9812c545d4587528a5ae','f0c8dc975bc5d2e1274da64d71693454','adjective','全面的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('eb528eb823d7b925007e2dfe3434b915','9c7a9b87867c9812c545d4587528a5ae','The report gives a comprehensive analysis.','这份报告给出了全面的分析。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='f0c8dc975bc5d2e1274da64d71693454' WHERE `id`='2311891a306fe49e5398fda56cae6334';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('7aec8ec6c458446a80ecaf92f234a16e','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('982dcf6a3b7791b08314184b839db244c14a4b6c362f47475d2167a763cdaf4f'),UNHEX('7beb7f4a57251263ca5d7274880aa4ba470a8b32e18885340ca2fd40a588fe23'),'published','senior','0a9ee8a6805a33b57970b91ae2ac6895',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('0a9ee8a6805a33b57970b91ae2ac6895','7aec8ec6c458446a80ecaf92f234a16e',1,'hypothesis','hypothesis','/haɪˈpɒθəsɪs/','假设','The experiment supports the hypothesis.','实验支持这个假设。','intro',15,'公开英语词表，仅作学习用途',UNHEX('7beb7f4a57251263ca5d7274880aa4ba470a8b32e18885340ca2fd40a588fe23'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('5fe61dee12e7fec59879c7f7edd44b96','0a9ee8a6805a33b57970b91ae2ac6895','noun','假设',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('37f859e3d763e3dcf5935826445a7f52','5fe61dee12e7fec59879c7f7edd44b96','The experiment supports the hypothesis.','实验支持这个假设。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='0a9ee8a6805a33b57970b91ae2ac6895' WHERE `id`='7aec8ec6c458446a80ecaf92f234a16e';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('ad66316f61a3ec14818a39c17ccb183a','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('b189ee028c9923e3dc9d8482f45dc1ce056b7cd5d81c5fe6eb8b07958ade4f2f'),UNHEX('84bb160039a7955385c44ea53025a2585c33ca4a40c9d2498f7cfc0784ecbd53'),'published','senior','c0356639d0fa30068df6adf3e7b73fa2',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('c0356639d0fa30068df6adf3e7b73fa2','ad66316f61a3ec14818a39c17ccb183a',1,'consequence','consequence','/ˈkɒnsɪkwəns/','结果，后果','Every choice has its consequences.','每个选择都有其后果。','intro',15,'公开英语词表，仅作学习用途',UNHEX('84bb160039a7955385c44ea53025a2585c33ca4a40c9d2498f7cfc0784ecbd53'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('0d2988f1392dbb016289f13122a53d33','c0356639d0fa30068df6adf3e7b73fa2','noun','结果，后果',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('a81d3f9cde077b9db94d50266cdfa71a','0d2988f1392dbb016289f13122a53d33','Every choice has its consequences.','每个选择都有其后果。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='c0356639d0fa30068df6adf3e7b73fa2' WHERE `id`='ad66316f61a3ec14818a39c17ccb183a';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('1c1182f00cedeeaa526fdb660bd57cb5','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('5aac2d6e910986d4ccca9f98365d7c6dc2dd3983e6d3b756df1cbc3aee74ff1e'),UNHEX('a65dd3628de58bb9f3eeac7e3fbc8508cd45e0c550cabd97271bba4e272719d0'),'published','senior','c0ad4a677bde0bf2860fbc1c2fe876b3',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('c0ad4a677bde0bf2860fbc1c2fe876b3','1c1182f00cedeeaa526fdb660bd57cb5',1,'demonstrate','demonstrate','/ˈdemənstreɪt/','证明，演示','The teacher demonstrated how to solve the problem.','老师演示了如何解决这个问题。','intro',15,'公开英语词表，仅作学习用途',UNHEX('a65dd3628de58bb9f3eeac7e3fbc8508cd45e0c550cabd97271bba4e272719d0'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('f7103e57689e99f91c2aafeed5bbf6f8','c0ad4a677bde0bf2860fbc1c2fe876b3','verb','证明，演示',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('c5efbaceae7273eb5c33741c136a5d76','f7103e57689e99f91c2aafeed5bbf6f8','The teacher demonstrated how to solve the problem.','老师演示了如何解决这个问题。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='c0ad4a677bde0bf2860fbc1c2fe876b3' WHERE `id`='1c1182f00cedeeaa526fdb660bd57cb5';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('d01e0be6cfac199365571af9d91cf304','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('df5363818e2ea29040dccc9a898c0d7e3766f397f91537433b616ae8f66a72cd'),UNHEX('e45c0ca14614471e97255d365ca87e0f277ff47a3073fd631f5eeaab458ceb6c'),'published','senior','aa17359186f76b1486ef5642fa775cf9',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('aa17359186f76b1486ef5642fa775cf9','d01e0be6cfac199365571af9d91cf304',1,'sophisticated','sophisticated','/səˈfɪstɪkeɪtɪd/','复杂的，精密的','This is a sophisticated machine.','这是一台精密的机器。','intro',15,'公开英语词表，仅作学习用途',UNHEX('e45c0ca14614471e97255d365ca87e0f277ff47a3073fd631f5eeaab458ceb6c'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('4c09655ebc27276d7839b8295774e66e','aa17359186f76b1486ef5642fa775cf9','adjective','复杂的，精密的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('0315394206bb43d70bcaa3b827e2c8a4','4c09655ebc27276d7839b8295774e66e','This is a sophisticated machine.','这是一台精密的机器。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='aa17359186f76b1486ef5642fa775cf9' WHERE `id`='d01e0be6cfac199365571af9d91cf304';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`word_key_hash`,`state`,`stage`,`published_version_id`,`published_at`,`row_version`) VALUES
('fd7baa34641d38c26ad992d0eefb8cf8','word','7918934720e8180ff4d0f1610c0e4aab',UNHEX('4d6675c60b71420f74c638aef33c3c3c467c5e718d983b4ee5ec9a96ad7113ba'),UNHEX('711415eb4348502ead97fca1cf869a6ed64c8111ddc748b9d7b7fab48974ad3e'),'published','senior','43faac24125573b9681203dbff55b3fe',NOW(3),1) ON DUPLICATE KEY UPDATE `state`='published', `stage`=VALUES(`stage`), `published_version_id`=VALUES(`published_version_id`);
INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`word_term`,`phonetic`,`meaning`,`example_text`,`example_translation`,`difficulty`,`estimated_seconds`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`) VALUES
('43faac24125573b9681203dbff55b3fe','fd7baa34641d38c26ad992d0eefb8cf8',1,'sustainable','sustainable','/səˈsteɪnəbl/','可持续的','We need sustainable development.','我们需要可持续发展。','intro',15,'公开英语词表，仅作学习用途',UNHEX('711415eb4348502ead97fca1cf869a6ed64c8111ddc748b9d7b7fab48974ad3e'),'approved',NOW(3),'00000000000000000000000000000002') ON DUPLICATE KEY UPDATE `review_status`='approved';
INSERT INTO `word_sense` (`id`,`content_version_id`,`part_of_speech`,`meaning`,`sort_no`) VALUES
('eb07d56418fb14c4de50730b1409a4b2','43faac24125573b9681203dbff55b3fe','adjective','可持续的',0) ON DUPLICATE KEY UPDATE `meaning`=VALUES(`meaning`);
INSERT INTO `word_example` (`id`,`sense_id`,`sentence`,`translation`,`sort_no`) VALUES
('8d9b38bf7b22675cccb65adb872bbd0e','eb07d56418fb14c4de50730b1409a4b2','We need sustainable development.','我们需要可持续发展。',0) ON DUPLICATE KEY UPDATE `sentence`=VALUES(`sentence`);
UPDATE `learning_content` SET `current_version_id`='43faac24125573b9681203dbff55b3fe' WHERE `id`='fd7baa34641d38c26ad992d0eefb8cf8';

-- ============================================================================
-- 第三部分 V3.1 英语多词库
-- ============================================================================
-- 知行日课 V3.1 英语多词库扩展 / MySQL 5.7.25
-- 原则：单词仍只存 learning_content；词库只保存“成员关系”，不复制词义/例句/音频。
SET NAMES utf8mb4;
SET @seed_now := UTC_TIMESTAMP(3);
START TRANSACTION;

CREATE TABLE IF NOT EXISTS vocabulary_book (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_name VARCHAR(100) NOT NULL,
 book_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'k12/university/postgraduate/study_abroad/general',
 level_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 description VARCHAR(500) NULL,
 source_name VARCHAR(200) NULL,
 source_url VARCHAR(2048) NULL,
 license_note VARCHAR(1000) NULL,
 word_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示缓存；关系表实际数量为权威',
 sort_no INT UNSIGNED NOT NULL DEFAULT 0,
 is_recommended TINYINT UNSIGNED NOT NULL DEFAULT 0,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_vocabulary_book_code(book_code),
 KEY idx_vocabulary_book_type_state(book_type,state,sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='英语词库定义';

CREATE TABLE IF NOT EXISTS vocabulary_book_word (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'learning_content.id；业务要求content_type=word',
 sort_no INT UNSIGNED NOT NULL DEFAULT 0,
 importance TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-5，服务端校验',
 is_core TINYINT UNSIGNED NOT NULL DEFAULT 0,
 source_level VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 source_ref VARCHAR(200) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_vocabulary_book_word(book_id,content_id),
 KEY idx_vbw_content(content_id,book_id),
 KEY idx_vbw_order(book_id,sort_no,id),
 CONSTRAINT fk_vbw_book FOREIGN KEY(book_id) REFERENCES vocabulary_book(id) ON DELETE CASCADE,
 CONSTRAINT fk_vbw_content FOREIGN KEY(content_id) REFERENCES learning_content(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词库与统一单词多对多关系';

CREATE TABLE IF NOT EXISTS user_vocabulary_book (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active/paused/completed/removed',
 daily_new_limit TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '0-10，服务端校验',
 selected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 paused_at DATETIME(3) NULL,
 completed_at DATETIME(3) NULL,
 last_studied_at DATETIME(3) NULL,
 row_version INT UNSIGNED NOT NULL DEFAULT 1,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_user_vocabulary_book(owner_id,book_id),
 KEY idx_uvb_owner(owner_id,state,updated_at),
 CONSTRAINT fk_uvb_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_uvb_book FOREIGN KEY(book_id) REFERENCES vocabulary_book(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户选择词库；单词学习事实仍使用现有learning_record等表';

INSERT INTO vocabulary_book
(id,book_code,book_name,book_type,level_code,description,source_name,license_note,sort_no,is_recommended,state,created_at,updated_at)
VALUES
(MD5('vocabulary-book:PRIMARY'),'PRIMARY','小学英语','k12','primary','小学英语基础词库','知行日课课标词库','项目已确认课标词表及自编学习内容',10,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:JUNIOR'),'JUNIOR','初中英语','k12','junior','初中英语词库','知行日课课标词库','项目已确认课标词表及自编学习内容',20,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:SENIOR'),'SENIOR','高中英语','k12','senior','高中必修+选择性必修','知行日课课标词库','项目已确认高中课程词表及自编学习内容',30,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:CET4'),'CET4','大学英语四级','university','cet4','大学英语四级词库','待配置','仅导入公开或获授权词表成员关系',40,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:CET6'),'CET6','大学英语六级','university','cet6','大学英语六级词库','待配置','仅导入公开或获授权词表成员关系',50,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:POSTGRAD'),'POSTGRAD','考研英语','postgraduate','postgrad','考研英语词库','待配置','仅导入公开或获授权词表成员关系',60,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:IELTS'),'IELTS','IELTS 雅思','study_abroad','ielts','雅思词库','待配置','正式导入前核验来源许可',70,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:TOEFL'),'TOEFL','TOEFL 托福','study_abroad','toefl','托福词库','待配置','正式导入前核验来源许可',80,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:GRE'),'GRE','GRE','study_abroad','gre','GRE高阶词库','待配置','正式导入前核验来源许可',90,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:OXFORD3000'),'OXFORD3000','Oxford 3000','general','oxford3000','通用核心英语词汇','Oxford 3000','正式导入前核验来源许可',100,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:OXFORD5000'),'OXFORD5000','Oxford 5000','general','oxford5000','中高级通用英语词汇','Oxford 5000','正式导入前核验来源许可',110,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:COMPUTER'),'COMPUTER','计算机英语','technology','computer','程序开发与计算机基础术语','待配置','正式导入前核验来源许可',120,0,'active',@seed_now,@seed_now)
ON DUPLICATE KEY UPDATE book_name=VALUES(book_name),description=VALUES(description),license_note=VALUES(license_note),sort_no=VALUES(sort_no),state='active',updated_at=@seed_now;

-- 现有 K12 数据迁移到新词库
INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:PRIMARY:',cv.content_id)),MD5('vocabulary-book:PRIMARY'),cv.content_id,0,0,1,'curriculum','英语-小学',@seed_now,@seed_now
FROM content_topic ct JOIN learning_topic lt ON lt.id=ct.topic_id
JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id
WHERE lt.name='英语-小学' AND lc.content_type='word' AND lc.state='published'
ON DUPLICATE KEY UPDATE updated_at=@seed_now;

INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:JUNIOR:',cv.content_id)),MD5('vocabulary-book:JUNIOR'),cv.content_id,0,0,1,'curriculum','英语-初中',@seed_now,@seed_now
FROM content_topic ct JOIN learning_topic lt ON lt.id=ct.topic_id
JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id
WHERE lt.name='英语-初中' AND lc.content_type='word' AND lc.state='published'
ON DUPLICATE KEY UPDATE updated_at=@seed_now;

INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:SENIOR:',cv.content_id)),MD5('vocabulary-book:SENIOR'),cv.content_id,0,
 CASE WHEN lt.name='英语-高中必修' THEN 3 ELSE 2 END,
 CASE WHEN lt.name='英语-高中必修' THEN 1 ELSE 0 END,
 CASE WHEN lt.name='英语-高中必修' THEN 'required' ELSE 'selective' END,lt.name,@seed_now,@seed_now
FROM content_topic ct JOIN learning_topic lt ON lt.id=ct.topic_id
JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id
WHERE lt.name IN ('英语-高中必修','英语-高中选择性必修') AND lc.content_type='word' AND lc.state='published'
ON DUPLICATE KEY UPDATE importance=VALUES(importance),is_core=VALUES(is_core),source_level=VALUES(source_level),source_ref=VALUES(source_ref),updated_at=@seed_now;

UPDATE vocabulary_book vb
SET word_count=(SELECT COUNT(*) FROM vocabulary_book_word vbw WHERE vbw.book_id=vb.id)
WHERE book_code IN ('PRIMARY','JUNIOR','SENIOR');

COMMIT;

-- 验证
SELECT book_code,book_name,book_type,level_code,word_count,state FROM vocabulary_book ORDER BY sort_no;
SELECT vb.book_code,COUNT(vbw.id) actual_count
FROM vocabulary_book vb LEFT JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id
GROUP BY vb.id,vb.book_code ORDER BY vb.sort_no;

-- 示例：查询一个单词属于哪些词库
-- SELECT cv.word_term,vb.book_code,vb.book_name
-- FROM vocabulary_book_word vbw
-- JOIN vocabulary_book vb ON vb.id=vbw.book_id
-- JOIN learning_content lc ON lc.id=vbw.content_id
-- JOIN content_version cv ON cv.id=lc.published_version_id
-- WHERE cv.word_term='ability';

-- ============================================================================
-- 第四部分 可选演示数据 默认关闭
-- ============================================================================
SET @include_demo_data := COALESCE(@include_demo_data, 0);
DELIMITER $$
DROP PROCEDURE IF EXISTS `seed_optional_demo_data`$$
CREATE PROCEDURE `seed_optional_demo_data`()
BEGIN
  IF @include_demo_data = 1 THEN
    -- 演示数据：为测试账号补齐「复习(review)」与「行动(action)」素材
    -- 复习走 review_schedule（个人 SRS 队列），行动走 weekly_action（周总结确认的行动）
    -- 可重复执行（固定 ID + ON DUPLICATE KEY）
    SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
    SET @u = 'b77868671d15460f941e1d90fe613134';

    -- ---------- 复习素材：knowledge_item + review_schedule ----------
    INSERT INTO `knowledge_item` (`id`,`owner_id`,`item_type`,`title`,`body`,`search_text`,`learning_status`,`verification_status`,`state`) VALUES
    ('d1000000000000000000000000000001',@u,'note','复习 Java 变量作用域','变量在哪个代码块声明，作用域就覆盖到哪里；局部变量只在方法内可见。','变量在哪个代码块声明，作用域就覆盖到哪里；局部变量只在方法内可见。','learning','unverified','active'),
    ('d1000000000000000000000000000002',@u,'note','复习 HTTP 状态码','2xx 成功、3xx 重定向、4xx 客户端错误、5xx 服务端错误。','2xx 成功、3xx 重定向、4xx 客户端错误、5xx 服务端错误。','learning','unverified','active'),
    ('d1000000000000000000000000000003',@u,'note','复习 SQL 索引','索引能加速查询，但会拖慢写入并占用空间，需按查询条件建。','索引能加速查询，但会拖慢写入并占用空间，需按查询条件建。','learning','unverified','active')
    ON DUPLICATE KEY UPDATE `state`='active';

    INSERT INTO `review_schedule` (`id`,`owner_id`,`knowledge_id`,`state`,`stage`,`due_date`) VALUES
    ('d2000000000000000000000000000001',@u,'d1000000000000000000000000000001','active',0,'2026-09-06'),
    ('d2000000000000000000000000000002',@u,'d1000000000000000000000000000002','active',0,'2026-09-05'),
    ('d2000000000000000000000000000003',@u,'d1000000000000000000000000000003','active',0,'2026-09-04')
    ON DUPLICATE KEY UPDATE `state`='active';

    -- ---------- 行动素材：weekly_summary + weekly_action ----------
    INSERT INTO `weekly_summary` (`id`,`owner_id`,`week_start`,`source_fingerprint`,`is_stale`) VALUES
    ('d3000000000000000000000000000001',@u,'2026-09-07',UNHEX(SHA2('demo-week-2026-09-07',256)),0)
    ON DUPLICATE KEY UPDATE `is_stale`=0;

    INSERT INTO `weekly_action` (`id`,`owner_id`,`summary_id`,`action_no`,`title`,`note`,`scheduled_date`,`estimated_minutes`,`state`,`confirmed_at`) VALUES
    ('d4000000000000000000000000000001',@u,'d3000000000000000000000000000001',1,'整理今天的学习目标','复盘今天的任务完成情况，明确明天重点','2026-09-06',10,'confirmed',NOW(3)),
    ('d4000000000000000000000000000002',@u,'d3000000000000000000000000000001',2,'列出下一步行动','为明天安排具体的学习计划','2026-09-06',15,'confirmed',NOW(3))
    ON DUPLICATE KEY UPDATE `state`='confirmed';
  END IF;
END$$
CALL `seed_optional_demo_data`()$$
DROP PROCEDURE IF EXISTS `seed_optional_demo_data`$$
DELIMITER ;

-- ============================================================================
-- 第五部分 V3.8-V3.15 已实现增补（最终结构）
-- ============================================================================
CREATE TABLE IF NOT EXISTS app_parameter (
  id CHAR(32) NOT NULL PRIMARY KEY,
  param_key VARCHAR(100) NOT NULL,
  param_value VARCHAR(4096) NOT NULL,
  is_secret TINYINT UNSIGNED NOT NULL DEFAULT 1,
  description VARCHAR(200) NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'active',
  version_no INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_app_parameter_key (param_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用运行参数；敏感值由服务端加密后保存';

-- ---- 合并自 V3.8_word_memory_training.sql ----
-- 知行日课 V3.1 英语记忆训练核心表 / MySQL 5.7.25
-- 执行前：备份、在副本演练、确认已先完成 V3.1 空库基线。本脚本不会自动启用功能开关。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS word_memory_hint (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_version_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sense_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 method_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 hint_body VARCHAR(1000) NOT NULL,
 level_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'editorial',
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'draft',
 hint_version INT UNSIGNED NOT NULL DEFAULT 1,
 withdrawn_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_word_memory_hint(content_version_id,sense_id,method_type,hint_version),
 KEY idx_word_memory_hint_pick(content_version_id,state,method_type),
 CONSTRAINT fk_wmh_version FOREIGN KEY(content_version_id) REFERENCES content_version(id) ON DELETE CASCADE,
 CONSTRAINT fk_wmh_sense FOREIGN KEY(sense_id) REFERENCES word_sense(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已审核公共记忆提示';

CREATE TABLE IF NOT EXISTS word_memory_question (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_version_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sense_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 dimension VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 prompt_text VARCHAR(1000) NOT NULL,
 expected_answer VARCHAR(1000) NOT NULL,
 accepted_answers_json TEXT NULL,
 answer_policy VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'exact',
 hint_text VARCHAR(1000) NULL,
 audio_required TINYINT UNSIGNED NOT NULL DEFAULT 0,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'draft',
 question_version INT UNSIGNED NOT NULL DEFAULT 1,
 withdrawn_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_word_memory_question(content_version_id,sense_id,dimension,question_version),
 KEY idx_word_memory_question_pick(content_version_id,dimension,state),
 CONSTRAINT fk_wmq_version FOREIGN KEY(content_version_id) REFERENCES content_version(id) ON DELETE CASCADE,
 CONSTRAINT fk_wmq_sense FOREIGN KEY(sense_id) REFERENCES word_sense(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已审核英语记忆题目及答案版本';

CREATE TABLE IF NOT EXISTS word_memory_session (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 return_to VARCHAR(500) NULL,
 task_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 business_date DATE NOT NULL,
 target_count TINYINT UNSIGNED NOT NULL,
 required_dimensions VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 add_to_review TINYINT UNSIGNED NOT NULL DEFAULT 0,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active',
 version_no INT UNSIGNED NOT NULL DEFAULT 1,
 idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 completed_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_word_memory_session_idem(owner_id,idempotency_key),
 KEY idx_word_memory_session_resume(owner_id,state,updated_at),
 CONSTRAINT fk_wms_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_wms_task FOREIGN KEY(task_id) REFERENCES daily_task(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可恢复的英语记忆训练会话';

CREATE TABLE IF NOT EXISTS word_memory_episode (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 session_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_version_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sense_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 question_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 question_version INT UNSIGNED NOT NULL,
 dimension VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 position_no INT UNSIGNED NOT NULL,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'pending',
 hint_used TINYINT UNSIGNED NOT NULL DEFAULT 0,
 answer_revealed TINYINT UNSIGNED NOT NULL DEFAULT 0,
 attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
 first_result VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 final_result VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_word_memory_episode(session_id,question_id),
 KEY idx_word_memory_episode_next(session_id,state,position_no),
 CONSTRAINT fk_wme_session FOREIGN KEY(session_id) REFERENCES word_memory_session(id) ON DELETE CASCADE,
 CONSTRAINT fk_wme_content FOREIGN KEY(content_id) REFERENCES learning_content(id) ON DELETE RESTRICT,
 CONSTRAINT fk_wme_version FOREIGN KEY(content_version_id) REFERENCES content_version(id) ON DELETE RESTRICT,
 CONSTRAINT fk_wme_sense FOREIGN KEY(sense_id) REFERENCES word_sense(id) ON DELETE SET NULL,
 CONSTRAINT fk_wme_question FOREIGN KEY(question_id) REFERENCES word_memory_question(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话中冻结的题目与重试状态';

CREATE TABLE IF NOT EXISTS word_memory_hint_event (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 session_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 episode_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 hint_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 answer_revealed TINYINT UNSIGNED NOT NULL DEFAULT 0,
 occurred_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 KEY idx_word_memory_hint_event_episode(episode_id,occurred_at),
 CONSTRAINT fk_wmhe_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_wmhe_session FOREIGN KEY(session_id) REFERENCES word_memory_session(id) ON DELETE CASCADE,
 CONSTRAINT fk_wmhe_episode FOREIGN KEY(episode_id) REFERENCES word_memory_episode(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可覆写的提示使用轨迹';

CREATE TABLE IF NOT EXISTS word_memory_attempt (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 session_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 episode_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempt_no TINYINT UNSIGNED NOT NULL,
 answer_text VARCHAR(1000) NULL,
 verdict VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 hint_used TINYINT UNSIGNED NOT NULL DEFAULT 0,
 first_attempt TINYINT UNSIGNED NOT NULL DEFAULT 0,
 duration_ms INT UNSIGNED NULL,
 idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 submitted_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_word_memory_attempt_no(episode_id,attempt_no),
 UNIQUE KEY uk_word_memory_attempt_idem(owner_id,idempotency_key),
 KEY idx_word_memory_attempt_session(session_id,episode_id),
 CONSTRAINT fk_wma_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_wma_session FOREIGN KEY(session_id) REFERENCES word_memory_session(id) ON DELETE CASCADE,
 CONSTRAINT fk_wma_episode FOREIGN KEY(episode_id) REFERENCES word_memory_episode(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可覆写的首答与重试事实';

CREATE TABLE IF NOT EXISTS word_memory_evidence (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sense_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 dimension VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 business_date DATE NOT NULL,
 episode_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 first_attempt_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 first_result VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 hint_used TINYINT UNSIGNED NOT NULL DEFAULT 0,
 rule_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_word_memory_evidence_episode(episode_id),
 KEY idx_word_memory_evidence_dimension(owner_id,content_id,sense_id,dimension,business_date),
 CONSTRAINT fk_wmev_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_wmev_content FOREIGN KEY(content_id) REFERENCES learning_content(id) ON DELETE RESTRICT,
 CONSTRAINT fk_wmev_sense FOREIGN KEY(sense_id) REFERENCES word_sense(id) ON DELETE SET NULL,
 CONSTRAINT fk_wmev_episode FOREIGN KEY(episode_id) REFERENCES word_memory_episode(id) ON DELETE CASCADE,
 CONSTRAINT fk_wmev_attempt FOREIGN KEY(first_attempt_id) REFERENCES word_memory_attempt(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按业务日保留的分维度首答证据';

-- ---- 合并自 V3.10_plan_settings_dictionary.sql ----
-- 计划设置数据字典 / MySQL 5.7.25
-- 页面只读取 /api/plans/settings 返回的白名单结构；不直接暴露 app_parameter。
SET NAMES utf8mb4;

INSERT INTO app_parameter (id,param_key,param_value,is_secret,description,state,version_no,created_at,updated_at) VALUES
('00000000000000000000000000000041','plan.daily_budget_min','1',0,'每日学习时间最小值（分钟）','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000042','plan.daily_budget_max','1440',0,'每日学习时间最大值（分钟）','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000043','plan.daily_budget_step','5',0,'每日学习时间加减步长（分钟）','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000044','plan.daily_budget_presets','[5,10,15,20,30]',0,'每日学习时间快捷值 JSON','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000045','plan.weekdays','[{"value":"0","label":"一"},{"value":"1","label":"二"},{"value":"2","label":"三"},{"value":"3","label":"四"},{"value":"4","label":"五"},{"value":"5","label":"六"},{"value":"6","label":"日"}]',0,'学习日选项 JSON；value 为周一至周日的位序','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000046','plan.difficulties','[{"value":"intro","label":"入门"},{"value":"advanced","label":"进阶"}]',0,'学习难度选项 JSON','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000047','plan.default_daily_budget_min','10',0,'默认每日学习分钟数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000048','plan.default_weekdays_mask','31',0,'默认学习日位图','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000049','plan.default_difficulty','intro',0,'默认学习难度','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000050','plan.default_tech_count','1',0,'默认每日技术新学数量','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000051','plan.default_new_word_count','3',0,'默认每日英语新词数量','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000052','plan.default_review_limit','5',0,'默认每日复习上限','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000053','plan.default_journal_enabled','true',0,'默认启用每日复盘','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000054','plan.default_review_enabled','true',0,'默认启用到期复习','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000055','plan.estimate_tech_seconds','180',0,'前端预算提示：每条技术内容预计秒数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000056','plan.estimate_word_seconds','30',0,'前端预算提示：每个英语新词预计秒数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000057','plan.estimate_journal_seconds','120',0,'前端预算提示：每日复盘预计秒数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000058','plan.estimate_review_seconds','30',0,'前端预算提示：每项复习预计秒数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000035','plan.tech_count_max','255',0,'技术新学每日数量上限','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000036','plan.new_word_count_max','255',0,'英语新词每日数量上限','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000037','plan.review_limit_max','255',0,'复习每日数量上限','active',1,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE
 version_no=IF(param_value=VALUES(param_value),version_no,version_no+1),
 param_value=VALUES(param_value),is_secret=0,description=VALUES(description),state='active',updated_at=NOW(3);

-- ---- 合并自 V3.11_learning_filter_dictionary.sql ----
-- 学习页筛选数据字典 / MySQL 5.7.25
-- 页面只读取 /api/learning/filters 返回的白名单结构；不直接暴露 app_parameter。
SET NAMES utf8mb4;

INSERT INTO app_parameter (id,param_key,param_value,is_secret,description,state,version_no,created_at,updated_at) VALUES
('00000000000000000000000000000059','learning.difficulties','[{"value":"intro","label":"入门"},{"value":"advanced","label":"进阶"}]',0,'学习内容难度筛选 JSON','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000060','learning.stages','[{"value":"primary","label":"小学"},{"value":"junior","label":"初中"},{"value":"senior","label":"高中"}]',0,'英语词汇学段筛选 JSON','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000061','learning.notebook_statuses','[{"value":"review","label":"待复习"},{"value":"familiar","label":"已熟悉"}]',0,'生词本状态筛选 JSON','active',1,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE
 version_no=IF(param_value=VALUES(param_value),version_no,version_no+1),
 param_value=VALUES(param_value),is_secret=0,description=VALUES(description),state='active',updated_at=NOW(3);

-- ---- 合并自 V3.12_ai_ask_and_model_management.sql ----
-- V3.12 问一问、模型管理与费用记录（MySQL 5.7.25）
CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `model_code` VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `display_name` VARCHAR(100) NOT NULL,
  `specification` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'flash/pro/自定义规格',
  `base_url` VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `api_key_param_key` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '仅引用 app_parameter 的密钥键',
  `enabled` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `is_default` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `max_output_tokens` SMALLINT UNSIGNED NOT NULL DEFAULT 1200,
  `timeout_seconds` SMALLINT UNSIGNED NOT NULL DEFAULT 60,
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_model_config` (`provider_code`,`model_code`),
  KEY `idx_ai_model_available` (`enabled`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI 模型运行配置';

INSERT INTO `ai_model_config` (`id`,`provider_code`,`model_code`,`display_name`,`specification`,`base_url`,`api_key_param_key`,`enabled`,`is_default`,`max_output_tokens`,`timeout_seconds`)
SELECT '00000000000000000000000000000031','deepseek','deepseek-flash','DeepSeek Flash','flash','https://api.deepseek.com/chat/completions','AI_DEEPSEEK_API_KEY',1,1,1200,60
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_model_config` WHERE `provider_code`='deepseek' AND `model_code`='deepseek-flash');

INSERT INTO `ai_model_config` (`id`,`provider_code`,`model_code`,`display_name`,`specification`,`base_url`,`api_key_param_key`,`enabled`,`is_default`,`max_output_tokens`,`timeout_seconds`)
SELECT '00000000000000000000000000000032','deepseek','deepseek-v4-pro','DeepSeek Pro','pro','https://api.deepseek.com/chat/completions','AI_DEEPSEEK_API_KEY',1,0,1200,60
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_model_config` WHERE `provider_code`='deepseek' AND `model_code`='deepseek-v4-pro');

-- 价格为 0 表示尚未由管理员核实；后端会阻止调用，避免使用过期价格导致费用失真。
INSERT INTO `ai_model_price` (`id`,`provider_code`,`model_code`,`version_no`,`currency`,`input_per_million`,`output_per_million`,`pricing_json`,`effective_at`)
SELECT '00000000000000000000000000000041','deepseek','deepseek-flash',1,'CNY',0,0,JSON_OBJECT('status','pending_review','source','https://api-docs.deepseek.com/quick_start/pricing/'),CURRENT_TIMESTAMP(3)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_model_price` WHERE `provider_code`='deepseek' AND `model_code`='deepseek-flash');

INSERT INTO `ai_model_price` (`id`,`provider_code`,`model_code`,`version_no`,`currency`,`input_per_million`,`output_per_million`,`pricing_json`,`effective_at`)
SELECT '00000000000000000000000000000042','deepseek','deepseek-v4-pro',1,'CNY',0,0,JSON_OBJECT('status','pending_review','source','https://api-docs.deepseek.com/quick_start/pricing/'),CURRENT_TIMESTAMP(3)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_model_price` WHERE `provider_code`='deepseek' AND `model_code`='deepseek-v4-pro');

-- ---- 合并自 V3.13_graded_articles_and_aliyun_tts.sql ----
-- 知行日课 V3.13：分级英语短文库 + 阿里云 TTS 音频缓存（MySQL 5.7.25）
-- 可重复执行；不在 SQL 中保存 AppKey、AccessKey 或 Token。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

DROP PROCEDURE IF EXISTS ensure_v313_column;
DELIMITER $$
CREATE PROCEDURE ensure_v313_column(IN p_table VARCHAR(64),IN p_column VARCHAR(64),IN p_definition VARCHAR(1000))
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_column) THEN
    SET @ddl=CONCAT('ALTER TABLE `',p_table,'` ADD COLUMN `',p_column,'` ',p_definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ensure_v313_column('content_version','article_audio_asset_id','CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''整篇朗读音频 media_asset.id''');
CALL ensure_v313_column('content_version','article_audio_voice','VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''合成音色；音色变更后重新生成''');
CALL ensure_v313_column('content_version','article_audio_generated_at','DATETIME(3) NULL COMMENT ''整篇音频生成时间 UTC''');
DROP PROCEDURE ensure_v313_column;

CREATE TABLE IF NOT EXISTS `tts_usage_log` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `target_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'english_article/word',
  `target_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'aliyun_nls',
  `voice` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `char_count` INT UNSIGNED NOT NULL,
  `state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'succeeded/failed',
  `provider_request_id` VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `asset_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `error_code` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_tts_usage_time` (`created_at`),
  KEY `idx_tts_usage_target` (`target_type`,`target_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='语音合成调用日志；不保存原文和凭据';

INSERT INTO `app_parameter` (`id`,`param_key`,`param_value`,`is_secret`,`description`,`state`,`version_no`) VALUES
(MD5('tts.aliyun.endpoint'),'tts.aliyun.endpoint','https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts',0,'阿里云 NLS 语音合成 HTTPS 地址','active',1),
(MD5('tts.aliyun.voice'),'tts.aliyun.voice','aixia',0,'阿里云 NLS 默认英语音色','active',1),
(MD5('tts.aliyun.sample_rate'),'tts.aliyun.sample_rate','16000',0,'阿里云 NLS 采样率','active',1)
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`state`='active';

INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`license_note`,`enabled`) VALUES
('e0000000000000000000000000000013','知行日课分级英语短文','manual',NULL,'原创分级学习短文，可在本产品内展示、朗读与跟读',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`license_note`=VALUES(`license_note`),`enabled`=1;

DROP PROCEDURE IF EXISTS seed_v313_articles;
DELIMITER $$
CREATE PROCEDURE seed_v313_articles()
BEGIN
  DECLARE n INT DEFAULT 1;
  DECLARE person_en VARCHAR(20); DECLARE person_zh VARCHAR(20);
  DECLARE place_en VARCHAR(40); DECLARE place_zh VARCHAR(40);
  DECLARE action_en VARCHAR(120); DECLARE action_zh VARCHAR(120);
  DECLARE result_en VARCHAR(120); DECLARE result_zh VARCHAR(120);
  DECLARE cid CHAR(32); DECLARE vid CHAR(32); DECLARE title_en VARCHAR(100);
  DECLARE text_en VARCHAR(1000); DECLARE text_zh VARCHAR(1000); DECLARE diff VARCHAR(32);

  WHILE n<=202 DO
    SET diff=IF(n<=101,'intro','advanced');
    SET person_en=ELT(1+MOD(n-1,11),'Mia','Leo','Anna','Ben','Lily','Tom','Nina','Jack','Emma','Ryan','Lucy');
    SET person_zh=ELT(1+MOD(n-1,11),'米娅','利奥','安娜','本','莉莉','汤姆','妮娜','杰克','艾玛','瑞恩','露西');
    SET place_en=ELT(1+MOD(FLOOR((n-1)/11),10),'library','park','kitchen','classroom','garden','station','shop','museum','home','playground');
    SET place_zh=ELT(1+MOD(FLOOR((n-1)/11),10),'图书馆','公园','厨房','教室','花园','车站','商店','博物馆','家里','操场');
    SET action_en=ELT(1+MOD(FLOOR((n-1)/7),10),'reads a short note','watches a small change','follows three clear steps','asks one useful question','compares two simple ideas','checks a map carefully','writes a new sentence','explains a picture','cleans a quiet corner','practices with a friend');
    SET action_zh=ELT(1+MOD(FLOOR((n-1)/7),10),'读一则短笔记','观察一个小变化','按照三个清晰步骤操作','提出一个有用的问题','比较两个简单观点','仔细查看地图','写下一个新句子','解释一幅图','收拾一个安静的角落','和朋友一起练习');
    SET result_en=ELT(1+MOD(FLOOR((n-1)/5),10),'finds one helpful detail','remembers the main idea','finishes the task calmly','shares a clear answer','learns from a small mistake','chooses the next step','feels ready to continue','helps another learner','records a useful example','ends the day with confidence');
    SET result_zh=ELT(1+MOD(FLOOR((n-1)/5),10),'找到一个有帮助的细节','记住了主要观点','从容地完成任务','分享一个清晰的答案','从一个小错误中学习','选好下一步','准备好继续前进','帮助了另一位学习者','记录一个有用的例子','充满信心地结束一天');
    IF diff='intro' THEN
      SET title_en=CONCAT(person_en,' at the ',UPPER(LEFT(place_en,1)),SUBSTRING(place_en,2),' ',LPAD(n,3,'0'));
      SET text_en=CONCAT('Today, ',person_en,' visits the ',place_en,'. ',person_en,' ',action_en,'. Before leaving, ',person_en,' ',result_en,'.');
      SET text_zh=CONCAT('今天，',person_zh,'来到',place_zh,'。',person_zh,action_zh,'。离开前，',person_zh,result_zh,'。');
    ELSE
      SET title_en=CONCAT('A Careful Decision ',LPAD(n-101,3,'0'),': ',UPPER(LEFT(place_en,1)),SUBSTRING(place_en,2));
      SET text_en=CONCAT('While working in the ',place_en,', ',person_en,' ',action_en,'. Instead of accepting the first result, ',person_en,' checks the evidence and ',result_en,'. The careful process makes the next decision easier to explain.');
      SET text_zh=CONCAT('在',place_zh,'工作时，',person_zh,action_zh,'。',person_zh,'没有直接接受第一个结果，而是核对证据，并',result_zh,'。这个严谨过程让下一个决定更容易解释。');
    END IF;
    SET cid=MD5(CONCAT('v313:article:',diff,':',n));
    SET vid=MD5(CONCAT('v313:article-version:',diff,':',n));
    INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at,row_version)
    VALUES(cid,'english_article','e0000000000000000000000000000013',UNHEX(SHA2(CONCAT('v313:',diff,':',n),256)),'published',vid,vid,UTC_TIMESTAMP(3),1)
    ON DUPLICATE KEY UPDATE state='published',current_version_id=VALUES(current_version_id),published_version_id=VALUES(published_version_id),published_at=COALESCE(published_at,VALUES(published_at));
    INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,origin_author,license_snapshot,body_hash,review_status,reviewed_at,created_by,article_blocks)
    VALUES(vid,cid,1,title_en,IF(diff='intro','入门分级英语短文','进阶分级英语短文'),text_en,diff,IF(diff='intro',75,120),'知行日课','原创分级学习短文',UNHEX(SHA2(text_en,256)),'approved',UTC_TIMESTAMP(3),'00000000000000000000000000000002',JSON_ARRAY(JSON_OBJECT('paragraph_id','p1','text',text_en,'translation',text_zh,'words',JSON_ARRAY())))
    ON DUPLICATE KEY UPDATE title=VALUES(title),summary=VALUES(summary),body=VALUES(body),difficulty=VALUES(difficulty),estimated_seconds=VALUES(estimated_seconds),article_blocks=VALUES(article_blocks),review_status='approved';
    INSERT INTO content_topic(id,content_version_id,topic_id)
    SELECT MD5(CONCAT('v313:topic:',diff,':',n)),vid,t.id FROM learning_topic t WHERE t.scope_key='system' AND t.normalized_name='技术英语' LIMIT 1
    ON DUPLICATE KEY UPDATE topic_id=VALUES(topic_id);
    SET n=n+1;
  END WHILE;
END$$
DELIMITER ;

START TRANSACTION;
CALL seed_v313_articles();
COMMIT;
DROP PROCEDURE seed_v313_articles;

-- 验收：intro 和 advanced 均应不少于 101 篇。
SELECT cv.difficulty,COUNT(*) AS article_count
FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id
WHERE lc.content_type='english_article' AND lc.state='published'
GROUP BY cv.difficulty ORDER BY cv.difficulty;

-- ---- 合并自 V3.15_article_word_glossary.sql ----
-- 知行日课 V3.15：短文点词词汇表（MySQL 5.7.25）
-- 覆盖 V3.13 预置短文中的全部 104 个英文词形；正式词库存在同词时由正式词库优先。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS `article_word_glossary` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `term` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `phonetic` VARCHAR(200) NOT NULL,
  `meaning` VARCHAR(500) NOT NULL,
  `audio_asset_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `audio_voice` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `audio_generated_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_article_word_glossary_term` (`term`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='短文点词补充词汇；正式词库优先';

INSERT INTO `article_word_glossary` (`id`,`term`,`phonetic`,`meaning`) VALUES
(MD5('article-glossary:a'),'a','/ə; eɪ/','一个；一'),
(MD5('article-glossary:accepting'),'accepting','/əkˈseptɪŋ/','接受；接纳'),
(MD5('article-glossary:and'),'and','/ənd/','和；并且'),
(MD5('article-glossary:anna'),'anna','/ˈænə/','安娜（人名）'),
(MD5('article-glossary:another'),'another','/əˈnʌðə(r)/','另一个；又一个'),
(MD5('article-glossary:answer'),'answer','/ˈɑːnsə(r)/','答案；回答'),
(MD5('article-glossary:asks'),'asks','/ɑːsks/','询问；请求'),
(MD5('article-glossary:before'),'before','/bɪˈfɔː(r)/','在……以前'),
(MD5('article-glossary:ben'),'ben','/ben/','本（人名）'),
(MD5('article-glossary:calmly'),'calmly','/ˈkɑːmli/','平静地；从容地'),
(MD5('article-glossary:careful'),'careful','/ˈkeəfl/','仔细的；谨慎的'),
(MD5('article-glossary:carefully'),'carefully','/ˈkeəfəli/','仔细地；谨慎地'),
(MD5('article-glossary:change'),'change','/tʃeɪndʒ/','变化；改变'),
(MD5('article-glossary:checks'),'checks','/tʃeks/','检查；核对'),
(MD5('article-glossary:chooses'),'chooses','/ˈtʃuːzɪz/','选择'),
(MD5('article-glossary:classroom'),'classroom','/ˈklɑːsruːm/','教室'),
(MD5('article-glossary:cleans'),'cleans','/kliːnz/','清洁；打扫'),
(MD5('article-glossary:clear'),'clear','/klɪə(r)/','清楚的；明确的'),
(MD5('article-glossary:compares'),'compares','/kəmˈpeəz/','比较'),
(MD5('article-glossary:confidence'),'confidence','/ˈkɒnfɪdəns/','信心；自信'),
(MD5('article-glossary:continue'),'continue','/kənˈtɪnjuː/','继续'),
(MD5('article-glossary:corner'),'corner','/ˈkɔːnə(r)/','角落'),
(MD5('article-glossary:day'),'day','/deɪ/','一天；白天'),
(MD5('article-glossary:decision'),'decision','/dɪˈsɪʒn/','决定'),
(MD5('article-glossary:detail'),'detail','/ˈdiːteɪl/','细节'),
(MD5('article-glossary:easier'),'easier','/ˈiːziə(r)/','更容易的'),
(MD5('article-glossary:emma'),'emma','/ˈemə/','艾玛（人名）'),
(MD5('article-glossary:ends'),'ends','/endz/','结束'),
(MD5('article-glossary:evidence'),'evidence','/ˈevɪdəns/','证据'),
(MD5('article-glossary:example'),'example','/ɪɡˈzɑːmpl/','例子；示例'),
(MD5('article-glossary:explain'),'explain','/ɪkˈspleɪn/','解释'),
(MD5('article-glossary:explains'),'explains','/ɪkˈspleɪnz/','解释；说明'),
(MD5('article-glossary:feels'),'feels','/fiːlz/','感到；感觉'),
(MD5('article-glossary:finds'),'finds','/faɪndz/','找到；发现'),
(MD5('article-glossary:finishes'),'finishes','/ˈfɪnɪʃɪz/','完成；结束'),
(MD5('article-glossary:first'),'first','/fɜːst/','第一；首先'),
(MD5('article-glossary:follows'),'follows','/ˈfɒləʊz/','跟随；遵循'),
(MD5('article-glossary:friend'),'friend','/frend/','朋友'),
(MD5('article-glossary:from'),'from','/frɒm/','来自；从'),
(MD5('article-glossary:garden'),'garden','/ˈɡɑːdn/','花园'),
(MD5('article-glossary:helpful'),'helpful','/ˈhelpfl/','有帮助的'),
(MD5('article-glossary:helps'),'helps','/helps/','帮助'),
(MD5('article-glossary:home'),'home','/həʊm/','家；家里'),
(MD5('article-glossary:idea'),'idea','/aɪˈdɪə/','想法；主意'),
(MD5('article-glossary:ideas'),'ideas','/aɪˈdɪəz/','想法；观点（复数）'),
(MD5('article-glossary:in'),'in','/ɪn/','在……里面'),
(MD5('article-glossary:instead'),'instead','/ɪnˈsted/','反而；代替'),
(MD5('article-glossary:jack'),'jack','/dʒæk/','杰克（人名）'),
(MD5('article-glossary:kitchen'),'kitchen','/ˈkɪtʃɪn/','厨房'),
(MD5('article-glossary:learner'),'learner','/ˈlɜːnə(r)/','学习者'),
(MD5('article-glossary:learns'),'learns','/lɜːnz/','学习；学会'),
(MD5('article-glossary:leaving'),'leaving','/ˈliːvɪŋ/','离开'),
(MD5('article-glossary:leo'),'leo','/ˈliːəʊ/','利奥（人名）'),
(MD5('article-glossary:library'),'library','/ˈlaɪbrəri/','图书馆'),
(MD5('article-glossary:lily'),'lily','/ˈlɪli/','莉莉（人名）'),
(MD5('article-glossary:lucy'),'lucy','/ˈluːsi/','露西（人名）'),
(MD5('article-glossary:main'),'main','/meɪn/','主要的'),
(MD5('article-glossary:makes'),'makes','/meɪks/','制作；使得'),
(MD5('article-glossary:map'),'map','/mæp/','地图'),
(MD5('article-glossary:mia'),'mia','/ˈmiːə/','米娅（人名）'),
(MD5('article-glossary:mistake'),'mistake','/mɪˈsteɪk/','错误'),
(MD5('article-glossary:museum'),'museum','/mjuˈziːəm/','博物馆'),
(MD5('article-glossary:new'),'new','/njuː/','新的'),
(MD5('article-glossary:next'),'next','/nekst/','下一个；接下来的'),
(MD5('article-glossary:nina'),'nina','/ˈniːnə/','妮娜（人名）'),
(MD5('article-glossary:note'),'note','/nəʊt/','笔记；记录'),
(MD5('article-glossary:of'),'of','/əv/','……的'),
(MD5('article-glossary:one'),'one','/wʌn/','一；一个'),
(MD5('article-glossary:park'),'park','/pɑːk/','公园'),
(MD5('article-glossary:picture'),'picture','/ˈpɪktʃə(r)/','图片；画面'),
(MD5('article-glossary:playground'),'playground','/ˈpleɪɡraʊnd/','操场；游乐场'),
(MD5('article-glossary:practices'),'practices','/ˈpræktɪsɪz/','练习'),
(MD5('article-glossary:process'),'process','/ˈprəʊses/','过程；流程'),
(MD5('article-glossary:question'),'question','/ˈkwestʃən/','问题'),
(MD5('article-glossary:quiet'),'quiet','/ˈkwaɪət/','安静的'),
(MD5('article-glossary:reads'),'reads','/riːdz/','阅读'),
(MD5('article-glossary:ready'),'ready','/ˈredi/','准备好的'),
(MD5('article-glossary:records'),'records','/rɪˈkɔːdz/','记录'),
(MD5('article-glossary:remembers'),'remembers','/rɪˈmembəz/','记得；记住'),
(MD5('article-glossary:result'),'result','/rɪˈzʌlt/','结果'),
(MD5('article-glossary:ryan'),'ryan','/ˈraɪən/','瑞恩（人名）'),
(MD5('article-glossary:sentence'),'sentence','/ˈsentəns/','句子'),
(MD5('article-glossary:shares'),'shares','/ʃeəz/','分享'),
(MD5('article-glossary:shop'),'shop','/ʃɒp/','商店'),
(MD5('article-glossary:short'),'short','/ʃɔːt/','短的'),
(MD5('article-glossary:simple'),'simple','/ˈsɪmpl/','简单的'),
(MD5('article-glossary:small'),'small','/smɔːl/','小的'),
(MD5('article-glossary:station'),'station','/ˈsteɪʃn/','车站'),
(MD5('article-glossary:step'),'step','/step/','步骤；一步'),
(MD5('article-glossary:steps'),'steps','/steps/','步骤（复数）'),
(MD5('article-glossary:task'),'task','/tɑːsk/','任务'),
(MD5('article-glossary:the'),'the','/ðə; ðiː/','这；该（定冠词）'),
(MD5('article-glossary:three'),'three','/θriː/','三'),
(MD5('article-glossary:to'),'to','/tə; tuː/','向；到；用于不定式'),
(MD5('article-glossary:today'),'today','/təˈdeɪ/','今天'),
(MD5('article-glossary:tom'),'tom','/tɒm/','汤姆（人名）'),
(MD5('article-glossary:two'),'two','/tuː/','二；两个'),
(MD5('article-glossary:useful'),'useful','/ˈjuːsfl/','有用的'),
(MD5('article-glossary:visits'),'visits','/ˈvɪzɪts/','参观；拜访'),
(MD5('article-glossary:watches'),'watches','/ˈwɒtʃɪz/','观看；观察'),
(MD5('article-glossary:while'),'while','/waɪl/','当……时；一段时间'),
(MD5('article-glossary:with'),'with','/wɪð/','和；带有'),
(MD5('article-glossary:working'),'working','/ˈwɜːkɪŋ/','工作；正在工作的'),
(MD5('article-glossary:writes'),'writes','/raɪts/','书写；写下')
ON DUPLICATE KEY UPDATE `phonetic`=VALUES(`phonetic`),`meaning`=VALUES(`meaning`),`updated_at`=CURRENT_TIMESTAMP(3);

SELECT COUNT(*) AS glossary_count FROM article_word_glossary;
