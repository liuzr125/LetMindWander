-- 破坏性回滚：会删除用户的词书选择记录，仅在确认无需保留数据时执行。
SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS user_vocabulary_book;
DROP TABLE IF EXISTS vocabulary_book_word;
DROP TABLE IF EXISTS vocabulary_book;
SET FOREIGN_KEY_CHECKS=1;
