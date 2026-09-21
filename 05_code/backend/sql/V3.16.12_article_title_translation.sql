-- Run once before deploying the backend that reads content_version.title_translation.
-- Existing article titles remain NULL until the article detail requests a translation.
ALTER TABLE `content_version`
  ADD COLUMN `title_translation` VARCHAR(200) NULL AFTER `summary`;
