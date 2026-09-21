-- 部署新版后端前执行。新版短文点词查询依赖此列。
-- 标记短文点词释义来源；原有预置词保持 seed，按需 AI 补充的词为 ai_generated。
ALTER TABLE `article_word_glossary`
  ADD COLUMN `source_kind` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'seed' AFTER `meaning`;
