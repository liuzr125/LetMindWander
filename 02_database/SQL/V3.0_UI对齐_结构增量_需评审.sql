-- V3.0 从43表R1结构升级的评审脚本；只运行一次；执行前按迁移说明核对实际结构。

-- 不含数据回填，不包含DROP；DDL隐式提交，须备份与维护窗口。

-- nickname非空约束留给回填验收后的第二阶段，不在本脚本自动收紧。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `app_user`
  ADD COLUMN `mobile_bound_at` DATETIME(3) NULL DEFAULT NULL COMMENT '微信手机号授权绑定时间；历史未授权账号为空',
  ADD COLUMN `profile_visibility` JSON NULL COMMENT '字段可见范围映射 real_name english_name birthday gender hobbies introduction；private/friends/public；缺省private',
  ADD COLUMN `hobbies_json` JSON NULL COMMENT '爱好字符串数组；建议最多10个，每个20字符；去重',
  ADD COLUMN `introduction` VARCHAR(500) NULL DEFAULT NULL COMMENT '自我介绍，最多500字符',
  MODIFY COLUMN `mobile` VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '注册授权的微信手机号，规范化国际格式；注册完成后业务只读；仅服务端授权流程可首次写入，不向好友返回明文',
  ADD KEY `idx_mobile` (`mobile`, `status`) COMMENT '手机号精确查找，只返回可添加的脱敏账号资料',
  ADD KEY `idx_nickname` (`nickname`, `id`) COMMENT '昵称前缀查询索引；包含模糊查询仍需受控扫描';

ALTER TABLE `learning_plan`
  MODIFY COLUMN `topic_mask` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '兼容R1旧主题位图；V3只读迁移字段，不再作为主题过滤依据';

ALTER TABLE `content_version`
  ADD COLUMN `article_blocks` JSON NULL COMMENT '英语短文段落数组：paragraph_id、原文、译文、关联词条content_id；高亮与译文按版本保持一致',
  MODIFY COLUMN `topic_mask` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '兼容R1旧主题位图；V3主题关系以content_topic为准',
  MODIFY COLUMN `phonetic` VARCHAR(200) NULL DEFAULT NULL COMMENT 'R1兼容音标；V3以pronunciation的口音及词义目标音标为准',
  MODIFY COLUMN `meaning` VARCHAR(500) NULL DEFAULT NULL COMMENT 'R1兼容释义；V3结构化词义以word_sense为准，旧客户端只读第一义摘要',
  MODIFY COLUMN `example_text` VARCHAR(1000) NULL DEFAULT NULL COMMENT 'R1兼容例句；V3以word_example为准',
  MODIFY COLUMN `example_translation` VARCHAR(1000) NULL DEFAULT NULL COMMENT 'R1兼容例句翻译；V3以word_example.translation为准';

ALTER TABLE `learning_record`
  ADD COLUMN `familiarity_percent` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '自评熟悉度0至100，NULL表示尚未自评；不自动等同学习或复习阶段';

ALTER TABLE `knowledge_item`
  ADD COLUMN `visibility` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'private' COMMENT 'private仅自己；friends所有当前好友含未来新增；selected指定好友',
  ADD COLUMN `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'draft草稿；active有效；deleted不可访问并进入清理',
  ADD COLUMN `note_parent_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '做笔记时关联原知识；只读引用，正文独立保存；原文删除后不扩大访问 逻辑关联 knowledge_item.id';

ALTER TABLE `ai_job`
  MODIFY COLUMN `action_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'explain_content/refine_journal/extract_problem/organize_note/polish_week；新增定时公共资源摘要resource_summary，按明确配置授权执行';

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
