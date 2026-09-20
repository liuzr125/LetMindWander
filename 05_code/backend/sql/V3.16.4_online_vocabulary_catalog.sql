-- V3.16.4 在线词书目录与不可变词条快照；不改动既有正式词库。
CREATE TABLE IF NOT EXISTS vocabulary_online_book (
  book_code VARCHAR(64) NOT NULL PRIMARY KEY,
  book_name VARCHAR(160) NOT NULL,
  edition_label VARCHAR(160) NOT NULL,
  provider_name VARCHAR(160) NOT NULL,
  category VARCHAR(32) NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  download_path VARCHAR(200),
  source_revision VARCHAR(64),
  expected_count INT,
  license_status VARCHAR(32) NOT NULL DEFAULT 'unknown',
  license_note VARCHAR(1000) NOT NULL,
  latest_dataset_id CHAR(32),
  actual_count INT NOT NULL DEFAULT 0,
  quality_summary LONGTEXT,
  last_synced_at TIMESTAMP(3) NULL DEFAULT NULL,
  del_is SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE IF NOT EXISTS vocabulary_online_word (
  id CHAR(32) NOT NULL PRIMARY KEY,
  dataset_id CHAR(32) NOT NULL,
  book_code VARCHAR(64) NOT NULL,
  row_no INT NOT NULL,
  source_word_id VARCHAR(120),
  word_term VARCHAR(80) NOT NULL,
  normalized_word VARCHAR(80) NOT NULL,
  phonetic_us VARCHAR(200),
  phonetic_uk VARCHAR(200),
  phonetic_status VARCHAR(32) NOT NULL DEFAULT 'source_unverified',
  normalized_json LONGTEXT NOT NULL,
  raw_json LONGTEXT NOT NULL,
  quality_flags_json LONGTEXT NOT NULL,
  del_is SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_online_word_row UNIQUE (dataset_id,row_no)
);

-- MySQL 8 幂等增列：保留既有任务。
SET @online_example_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tts_generation_task' AND column_name='example_no');
SET @online_example_ddl = IF(@online_example_col=0,'ALTER TABLE tts_generation_task ADD COLUMN example_no INT NOT NULL DEFAULT 0 AFTER sense_no','SELECT 1');
PREPARE online_example_stmt FROM @online_example_ddl;
EXECUTE online_example_stmt;
DEALLOCATE PREPARE online_example_stmt;

INSERT INTO vocabulary_online_book(book_code,book_name,edition_label,provider_name,category,source_url,download_path,source_revision,expected_count,license_status,license_note)
SELECT 'KaoYan_2','考研英语词汇','数据集 KaoYan_2；考试年份未注明','有道词表 / kajweb 镜像','dataset','https://github.com/kajweb/dict','1521164654696_KaoYan_2.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',4533,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布。' WHERE NOT EXISTS (SELECT 1 FROM vocabulary_online_book WHERE book_code='KaoYan_2');
INSERT INTO vocabulary_online_book(book_code,book_name,edition_label,provider_name,category,source_url,download_path,source_revision,expected_count,license_status,license_note)
SELECT 'KaoYan_3','新东方考研词汇','数据集 KaoYan_3；不等同于新版绿宝书','新东方词表 / kajweb 镜像','dataset','https://github.com/kajweb/dict','1521164658897_KaoYan_3.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',3728,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布。' WHERE NOT EXISTS (SELECT 1 FROM vocabulary_online_book WHERE book_code='KaoYan_3');
INSERT INTO vocabulary_online_book(book_code,book_name,edition_label,provider_name,category,source_url,download_path,source_revision,expected_count,license_status,license_note)
SELECT 'KaoYan_1','考研必考词汇（正序版）','数据集 KaoYan_1；考试年份未注明','有道词表 / kajweb 镜像','dataset','https://github.com/kajweb/dict','1521164669833_KaoYan_1.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',1341,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布。' WHERE NOT EXISTS (SELECT 1 FROM vocabulary_online_book WHERE book_code='KaoYan_1');
INSERT INTO vocabulary_online_book(book_code,book_name,edition_label,provider_name,category,source_url,download_path,source_revision,expected_count,license_status,license_note)
SELECT 'KaoYanluan_1','考研必考词汇（乱序版）','数据集 KaoYanluan_1；考试年份未注明','有道词表 / kajweb 镜像','dataset','https://github.com/kajweb/dict','1521164661106_KaoYanluan_1.zip','3992bcb94c800a2fd38a9fd6ff95b2353e755363',1341,'unknown','公开镜像未提供明确数据再发布授权；仅暂存审核，不自动发布。' WHERE NOT EXISTS (SELECT 1 FROM vocabulary_online_book WHERE book_code='KaoYanluan_1');
INSERT INTO vocabulary_online_book(book_code,book_name,edition_label,provider_name,category,source_url,download_path,source_revision,expected_count,license_status,license_note)
SELECT 'POPULAR_HONGBAO_2027','红宝书·考研英语词汇','2027 版；具体装帧待选择','红宝书','popular','https://www.bilibili.com/video/BV1njz9BUEb3/',NULL,NULL,NULL,'metadata_only','仅收录书目元数据，未取得对应电子词表与再发布授权。' WHERE NOT EXISTS (SELECT 1 FROM vocabulary_online_book WHERE book_code='POPULAR_HONGBAO_2027');
INSERT INTO vocabulary_online_book(book_code,book_name,edition_label,provider_name,category,source_url,download_path,source_revision,expected_count,license_status,license_note)
SELECT 'POPULAR_SHANGUO_2027','考研词汇闪过','2027 考频·经典版；商品页标注待核验','闪过英语','popular','https://product.dangdang.com/12358940344.html',NULL,NULL,NULL,'metadata_only','仅收录书目元数据，未取得对应电子词表与再发布授权。' WHERE NOT EXISTS (SELECT 1 FROM vocabulary_online_book WHERE book_code='POPULAR_SHANGUO_2027');
INSERT INTO vocabulary_online_book(book_code,book_name,edition_label,provider_name,category,source_url,download_path,source_revision,expected_count,license_status,license_note)
SELECT 'POPULAR_LIANLIAN_2027','恋练有词：考研英语真题词汇6500分层串记','2027 全新版 / 180°平铺版','新东方 / 群言出版社','popular','https://www.yuntaigo.com/book.action?recordid=bm1obG96a2M5Nzg3NTE5MzExMTU1',NULL,NULL,NULL,'metadata_only','仅收录书目元数据，未取得对应电子词表与再发布授权。' WHERE NOT EXISTS (SELECT 1 FROM vocabulary_online_book WHERE book_code='POPULAR_LIANLIAN_2027');
INSERT INTO vocabulary_online_book(book_code,book_name,edition_label,provider_name,category,source_url,download_path,source_revision,expected_count,license_status,license_note)
SELECT 'POPULAR_LVBAO_2027','考研英语词汇词根+联想记忆法：乱序版','商品页标注适用2027；并非出版年份','新东方 / 群言出版社','popular','https://product.dangdang.com/29775283.html',NULL,NULL,NULL,'metadata_only','仅收录书目元数据，未取得对应电子词表与再发布授权。' WHERE NOT EXISTS (SELECT 1 FROM vocabulary_online_book WHERE book_code='POPULAR_LVBAO_2027');
