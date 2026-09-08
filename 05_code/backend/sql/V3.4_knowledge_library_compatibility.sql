-- 知识库 V3.4 兼容性增量脚本（MySQL 5.7.25）
-- 原则：不删除、不重建任何已有业务表；只补充缺失字段和缺失的既有模型表。
-- 执行前请先备份，并在目标库运行下方的结构核查查询。

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('knowledge_item','knowledge_revision','knowledge_tag','review_schedule','friend_relation','knowledge_share_rule')
ORDER BY table_name;

DELIMITER $$

DROP PROCEDURE IF EXISTS ensure_knowledge_column$$
CREATE PROCEDURE ensure_knowledge_column(IN p_column VARCHAR(64), IN p_definition VARCHAR(1000))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'knowledge_item' AND column_name = p_column
  ) THEN
    SET @knowledge_ddl = CONCAT('ALTER TABLE `knowledge_item` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE knowledge_stmt FROM @knowledge_ddl;
    EXECUTE knowledge_stmt;
    DEALLOCATE PREPARE knowledge_stmt;
  END IF;
END$$

CALL ensure_knowledge_column('problem_json', 'JSON NULL COMMENT ''问题卡结构化字段''')$$
CALL ensure_knowledge_column('last_verified_date', 'DATE NULL DEFAULT NULL COMMENT ''最后主动验证日期''')$$
CALL ensure_knowledge_column('mastered_at', 'DATETIME(3) NULL DEFAULT NULL COMMENT ''最近一次自评掌握时间''')$$
CALL ensure_knowledge_column('reuse_count', 'INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''主动复用次数''')$$
CALL ensure_knowledge_column('last_reused_at', 'DATETIME(3) NULL DEFAULT NULL COMMENT ''最近主动复用时间''')$$
CALL ensure_knowledge_column('source_journal_id', 'CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT ''来源复盘ID''')$$
CALL ensure_knowledge_column('source_journal_revision', 'INT UNSIGNED NULL DEFAULT NULL COMMENT ''来源复盘版本''')$$
CALL ensure_knowledge_column('source_ai_job_id', 'CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT ''来源AI任务ID''')$$
CALL ensure_knowledge_column('note_parent_id', 'CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT ''只读关联的原知识ID''')$$

DROP PROCEDURE IF EXISTS ensure_knowledge_column$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS `knowledge_revision` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision_no` INT UNSIGNED NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `change_kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_revision_no` (`knowledge_id`,`revision_no`),
  KEY `idx_knowledge_revision_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识修订快照';

CREATE TABLE IF NOT EXISTS `knowledge_tag` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `tag_name` VARCHAR(20) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_tag_name` (`knowledge_id`,`tag_name`),
  KEY `idx_knowledge_tag_owner_name` (`owner_id`,`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识标签关联';

CREATE TABLE IF NOT EXISTS `knowledge_share_rule` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `knowledge_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `friend_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `relation_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `relation_generation` INT UNSIGNED NOT NULL,
  `effect` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_share_friend` (`knowledge_id`,`friend_id`),
  KEY `idx_knowledge_share_friend_effect` (`friend_id`,`effect`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识指定授权与排除';

-- 结果核查：字段缺失数应为 0。
SELECT expected.column_name AS missing_column
FROM (
  SELECT 'problem_json' column_name UNION ALL SELECT 'last_verified_date' UNION ALL SELECT 'mastered_at'
  UNION ALL SELECT 'reuse_count' UNION ALL SELECT 'last_reused_at' UNION ALL SELECT 'source_journal_id'
  UNION ALL SELECT 'source_journal_revision' UNION ALL SELECT 'source_ai_job_id' UNION ALL SELECT 'note_parent_id'
) expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'knowledge_item'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL;
