-- V3.16.6 统一正式词书与在线来源目录；MySQL 5.7.25，执行前请备份，并以 utf8mb4 客户端字符集执行。
-- 在线目录只登记候选来源。unknown / metadata_only 仍不能越过既有授权与发布门禁。

SET @catalog_sort_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='vocabulary_online_book' AND column_name='sort_no');
SET @catalog_sort_ddl = IF(@catalog_sort_col=0,'ALTER TABLE vocabulary_online_book ADD COLUMN sort_no INT NOT NULL DEFAULT 0 AFTER category','SELECT 1');
PREPARE catalog_sort_stmt FROM @catalog_sort_ddl;
EXECUTE catalog_sort_stmt;
DEALLOCATE PREPARE catalog_sort_stmt;

INSERT INTO vocabulary_book
(id,book_code,book_name,book_type,level_code,description,source_name,license_note,word_count,sort_no,is_recommended,state)
VALUES
(MD5('vocabulary-book:COMPUTER'),'COMPUTER','计算机英语','technology','computer','程序开发与计算机基础术语','待配置','正式导入前核验来源许可',0,120,0,'active')
ON DUPLICATE KEY UPDATE book_name=VALUES(book_name),book_type=VALUES(book_type),level_code=VALUES(level_code),description=VALUES(description),sort_no=VALUES(sort_no),state='active';

INSERT INTO vocabulary_online_book
(book_code,book_name,edition_label,provider_name,category,sort_no,source_url,download_path,source_revision,expected_count,license_status,license_note)
VALUES
('PRIMARY_CATALOG','小学英语','核心书目；具体教材版本待选择','待接入授权词表','core',10,'https://www.pep.com.cn/',NULL,NULL,NULL,'metadata_only','仅登记与正式词书一致的书目；小学各年级和教材版本不能混作一份词表，需选择并核验来源后导入。'),
('ChuZhong_2','初中英语','有道初中英语词汇（正序版）；非现行教材版本声明','有道词表 / kajweb 镜像','core',20,'https://github.com/kajweb/dict','1521164647926_ChuZhong_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',1420,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布，也不声明覆盖现行教材。'),
('GaoZhong_2','高中英语','高中英语词汇（正序版）；非现行教材版本声明','有道词表 / kajweb 镜像','core',30,'https://github.com/kajweb/dict','1521164675301_GaoZhong_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',3668,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布，也不声明覆盖现行教材。'),
('CET4_2','大学英语四级','有道四级英语词汇（正序版）；考试年份未注明','有道词表 / kajweb 镜像','core',40,'https://github.com/kajweb/dict','1521164635506_CET4_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',3739,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布，不冒充当年考试大纲。'),
('CET6_2','大学英语六级','有道六级英语词汇；考试年份未注明','有道词表 / kajweb 镜像','core',50,'https://github.com/kajweb/dict','1524052554766_CET6_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',2078,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布，不冒充当年考试大纲。'),
('KaoYan_2','考研英语','有道考研英语词汇；考试年份未注明','有道词表 / kajweb 镜像','core',60,'https://github.com/kajweb/dict','1521164654696_KaoYan_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',4533,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布，不冒充当年考试大纲。'),
('IELTS_2','IELTS 雅思','有道雅思词汇（正序版）；考试年份未注明','有道词表 / kajweb 镜像','core',70,'https://github.com/kajweb/dict','1521164657744_IELTS_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',3427,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布。'),
('TOEFL_2','TOEFL 托福','有道 TOEFL 词汇；考试年份未注明','有道词表 / kajweb 镜像','core',80,'https://github.com/kajweb/dict','1521164640451_TOEFL_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',9213,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布。'),
('GRE_2','GRE','有道 GRE 词汇；考试年份未注明','有道词表 / kajweb 镜像','core',90,'https://github.com/kajweb/dict','1521164637271_GRE_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',7199,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布。'),
('OXFORD3000_CATALOG','Oxford 3000','核心书目；具体版本待核验','Oxford Learner''s Dictionaries','core',100,'https://www.oxfordlearnersdictionaries.com/wordlists/oxford3000-5000',NULL,NULL,NULL,'metadata_only','仅登记书目与官方说明页；未取得可再发布的电子词表，不自动抓取。'),
('OXFORD5000_CATALOG','Oxford 5000','核心书目；具体版本待核验','Oxford Learner''s Dictionaries','core',110,'https://www.oxfordlearnersdictionaries.com/wordlists/oxford3000-5000',NULL,NULL,NULL,'metadata_only','仅登记书目与官方说明页；未取得可再发布的电子词表，不自动抓取。'),
('COMPUTER_CATALOG','计算机英语','程序开发与计算机基础术语候选词表','开源候选书目','core',120,'https://github.com/JumpX/ZZ-1700-Words-Of-Computer',NULL,NULL,NULL,'metadata_only','仅登记候选来源；尚未核验许可、结构和词条质量，不提供自动下载与发布。')
ON DUPLICATE KEY UPDATE
book_name=VALUES(book_name),edition_label=VALUES(edition_label),provider_name=VALUES(provider_name),category=VALUES(category),sort_no=VALUES(sort_no),source_url=VALUES(source_url),download_path=VALUES(download_path),source_revision=VALUES(source_revision),expected_count=VALUES(expected_count),
license_note=IF(last_synced_at IS NULL AND actual_count=0,VALUES(license_note),license_note),updated_at=CURRENT_TIMESTAMP(3);

UPDATE vocabulary_online_book SET category='alternative',sort_no=210 WHERE book_code='KaoYan_3';
UPDATE vocabulary_online_book SET category='alternative',sort_no=220 WHERE book_code='KaoYan_1';
UPDATE vocabulary_online_book SET category='alternative',sort_no=230 WHERE book_code='KaoYanluan_1';
UPDATE vocabulary_online_book SET sort_no=310 WHERE book_code='POPULAR_HONGBAO_2027';
UPDATE vocabulary_online_book SET sort_no=320 WHERE book_code='POPULAR_SHANGUO_2027';
UPDATE vocabulary_online_book SET sort_no=330 WHERE book_code='POPULAR_LIANLIAN_2027';
UPDATE vocabulary_online_book SET sort_no=340 WHERE book_code='POPULAR_LVBAO_2027';
