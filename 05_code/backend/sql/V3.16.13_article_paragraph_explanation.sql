-- Run once before deploying the paragraph explanation endpoint.
-- Explanations are shared by published article version, paragraph ID and exact English text hash.
CREATE TABLE IF NOT EXISTS `article_paragraph_explanation` (
  `id` CHAR(32) NOT NULL,
  `content_version_id` CHAR(32) NOT NULL,
  `paragraph_id` VARCHAR(64) NOT NULL,
  `source_hash` BINARY(32) NOT NULL,
  `explanation` MEDIUMTEXT NULL,
  `state` VARCHAR(16) NOT NULL DEFAULT 'generating',
  `claim_token` CHAR(32) NULL,
  `lease_until` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_paragraph_explanation` (`content_version_id`, `paragraph_id`, `source_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
