-- V3.14 回滚：只删除短文补充词汇及其引用，不删除已生成的 media_asset/OSS 对象。
DROP TABLE IF EXISTS article_word_glossary;
