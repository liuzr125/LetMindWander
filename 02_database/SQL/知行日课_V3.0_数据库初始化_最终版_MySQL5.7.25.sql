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
  `purpose` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'avatar/knowledge_image/word_audio/example_audio',
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
