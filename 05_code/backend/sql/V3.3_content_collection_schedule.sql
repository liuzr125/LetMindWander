-- 每日官方技术资料采集任务（MySQL 5.7.25）。
-- 仅创建独立调度/运行记录表和受限来源；不修改、不删除已有学习内容。
-- 服务端在 Asia/Shanghai 的每日 22:00 触发，并将抓取结果以 pending 审核状态写入
-- learning_content/content_version，管理员审核后方可发布。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS `content_collection_schedule` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `cron_expression` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `timezone` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `per_run_limit` SMALLINT UNSIGNED NOT NULL DEFAULT 10,
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active',
  `last_run_at` DATETIME(3) NULL DEFAULT NULL,
  `next_run_at` DATETIME(3) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_collection_schedule_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='官方技术资料采集定时任务';

CREATE TABLE IF NOT EXISTS `content_collection_run` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `schedule_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trigger_key` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'running',
  `fetched_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `inserted_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `skipped_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `error_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
  `error_message` VARCHAR(500) NULL DEFAULT NULL,
  `started_at` DATETIME(3) NULL DEFAULT NULL,
  `finished_at` DATETIME(3) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_run_trigger` (`schedule_id`,`trigger_key`),
  KEY `idx_collection_run_history` (`schedule_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='官方技术资料采集运行记录';

-- 白名单只接受此处精确域名的 HTTPS RSS/Atom，不支持管理员输入任意抓取 URL。
INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`allowed_host`,`license_note`,`enabled`) VALUES
('00000000000000000000000000000081','Kubernetes 官方 RSS','rss','https://kubernetes.io/feed.xml','kubernetes.io','仅保存 RSS 标题与受限摘要；原文版权归 Kubernetes Authors，管理员审核后方可发布',1),
('00000000000000000000000000000082','Docker Blog 官方 RSS','rss','https://www.docker.com/feed/','www.docker.com','仅保存 RSS 标题与受限摘要；原文版权归 Docker Inc.，管理员审核后方可发布',1),
('00000000000000000000000000000083','OpenAI News 官方 RSS','rss','https://openai.com/news/rss.xml','openai.com','仅保存 RSS 标题与受限摘要；原文版权归 OpenAI，管理员审核后方可发布',1),
('00000000000000000000000000000084','Spring Blog 官方 Atom','rss','https://spring.io/blog.atom','spring.io','仅保存 Atom 标题与受限摘要；原文版权归 Broadcom/Spring，管理员审核后方可发布',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`source_type`=VALUES(`source_type`),`url`=VALUES(`url`),`allowed_host`=VALUES(`allowed_host`),`license_note`=VALUES(`license_note`),`enabled`=VALUES(`enabled`);

INSERT INTO `content_collection_schedule` (`id`,`name`,`cron_expression`,`timezone`,`per_run_limit`,`state`,`next_run_at`) VALUES
('00000000000000000000000000000090','每日官方技术资料采集','0 0 22 * * *','Asia/Shanghai',10,'active',UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`cron_expression`=VALUES(`cron_expression`),`timezone`=VALUES(`timezone`),`per_run_limit`=VALUES(`per_run_limit`),`state`='active';

-- 导入后核验：应返回 1 条 active 任务、4 个 RSS 来源；每次运行结果可在管理端“任务与运行”查看。
SELECT id,name,cron_expression,timezone,per_run_limit,state,next_run_at FROM content_collection_schedule;
SELECT id,name,url,allowed_host,enabled FROM content_source WHERE id IN
('00000000000000000000000000000081','00000000000000000000000000000082','00000000000000000000000000000083','00000000000000000000000000000084');
