-- V3.9 学习前选择英语词书 / MySQL 5.7.25
-- 幂等运行；单词主数据仍位于 learning_content/content_version。
SET NAMES utf8mb4;
SET @seed_now := UTC_TIMESTAMP(3);
START TRANSACTION;

CREATE TABLE IF NOT EXISTS vocabulary_book (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_name VARCHAR(100) NOT NULL,
 book_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 level_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 description VARCHAR(500) NULL,
 source_name VARCHAR(200) NULL,
 source_url VARCHAR(2048) NULL,
 license_note VARCHAR(1000) NULL,
 word_count INT UNSIGNED NOT NULL DEFAULT 0,
 sort_no INT UNSIGNED NOT NULL DEFAULT 0,
 is_recommended TINYINT UNSIGNED NOT NULL DEFAULT 0,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_vocabulary_book_code(book_code),
 KEY idx_vocabulary_book_type_state(book_type,state,sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='英语词库定义';

CREATE TABLE IF NOT EXISTS vocabulary_book_word (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sort_no INT UNSIGNED NOT NULL DEFAULT 0,
 importance TINYINT UNSIGNED NOT NULL DEFAULT 0,
 is_core TINYINT UNSIGNED NOT NULL DEFAULT 0,
 source_level VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 source_ref VARCHAR(200) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_vocabulary_book_word(book_id,content_id),
 KEY idx_vbw_content(content_id,book_id),
 KEY idx_vbw_order(book_id,sort_no,id),
 CONSTRAINT fk_vbw_book FOREIGN KEY(book_id) REFERENCES vocabulary_book(id) ON DELETE CASCADE,
 CONSTRAINT fk_vbw_content FOREIGN KEY(content_id) REFERENCES learning_content(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词书与统一单词多对多关系';

CREATE TABLE IF NOT EXISTS user_vocabulary_book (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 book_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active',
 daily_new_limit TINYINT UNSIGNED NOT NULL DEFAULT 3,
 selected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 paused_at DATETIME(3) NULL,
 completed_at DATETIME(3) NULL,
 last_studied_at DATETIME(3) NULL,
 row_version INT UNSIGNED NOT NULL DEFAULT 1,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_user_vocabulary_book(owner_id,book_id),
 KEY idx_uvb_owner(owner_id,state,updated_at),
 CONSTRAINT fk_uvb_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_uvb_book FOREIGN KEY(book_id) REFERENCES vocabulary_book(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户所选英语词书';

INSERT INTO vocabulary_book
(id,book_code,book_name,book_type,level_code,description,source_name,license_note,sort_no,is_recommended,state,created_at,updated_at)
VALUES
(MD5('vocabulary-book:PRIMARY'),'PRIMARY','小学英语','k12','primary','小学英语基础词库','知行日课课标词库','项目已确认课标词表及自编学习内容',10,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:JUNIOR'),'JUNIOR','初中英语','k12','junior','初中英语词库','知行日课课标词库','项目已确认课标词表及自编学习内容',20,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:SENIOR'),'SENIOR','高中英语','k12','senior','高中必修+选择性必修','知行日课课标词库','项目已确认高中课程词表及自编学习内容',30,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:CET4'),'CET4','大学英语四级','university','cet4','大学英语四级词库','待配置','仅导入公开或获授权词表成员关系',40,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:CET6'),'CET6','大学英语六级','university','cet6','大学英语六级词库','待配置','仅导入公开或获授权词表成员关系',50,1,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:POSTGRAD'),'POSTGRAD','考研英语','postgraduate','postgrad','考研英语词库','待配置','仅导入公开或获授权词表成员关系',60,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:IELTS'),'IELTS','IELTS 雅思','study_abroad','ielts','雅思词库','待配置','正式导入前核验来源许可',70,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:TOEFL'),'TOEFL','TOEFL 托福','study_abroad','toefl','托福词库','待配置','正式导入前核验来源许可',80,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:GRE'),'GRE','GRE','study_abroad','gre','GRE 高阶词库','待配置','正式导入前核验来源许可',90,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:OXFORD3000'),'OXFORD3000','Oxford 3000','general','oxford3000','通用核心英语词汇','Oxford 3000','正式导入前核验来源许可',100,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:OXFORD5000'),'OXFORD5000','Oxford 5000','general','oxford5000','中高级通用英语词汇','Oxford 5000','正式导入前核验来源许可',110,0,'active',@seed_now,@seed_now),
(MD5('vocabulary-book:COMPUTER'),'COMPUTER','计算机英语','technology','computer','程序开发与计算机基础术语','待配置','正式导入前核验来源许可',120,0,'active',@seed_now,@seed_now)
ON DUPLICATE KEY UPDATE book_name=VALUES(book_name),description=VALUES(description),license_note=VALUES(license_note),sort_no=VALUES(sort_no),state='active',updated_at=@seed_now;

INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:PRIMARY:',cv.content_id)),MD5('vocabulary-book:PRIMARY'),cv.content_id,0,0,1,'curriculum','英语-小学',@seed_now,@seed_now
FROM content_topic ct JOIN learning_topic lt ON lt.id=ct.topic_id JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id
WHERE lt.name='英语-小学' AND lc.content_type='word' AND lc.state='published'
ON DUPLICATE KEY UPDATE updated_at=@seed_now;

INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:JUNIOR:',cv.content_id)),MD5('vocabulary-book:JUNIOR'),cv.content_id,0,0,1,'curriculum','英语-初中',@seed_now,@seed_now
FROM content_topic ct JOIN learning_topic lt ON lt.id=ct.topic_id JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id
WHERE lt.name='英语-初中' AND lc.content_type='word' AND lc.state='published'
ON DUPLICATE KEY UPDATE updated_at=@seed_now;

INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:SENIOR:',cv.content_id)),MD5('vocabulary-book:SENIOR'),cv.content_id,0,CASE WHEN lt.name='英语-高中必修' THEN 3 ELSE 2 END,CASE WHEN lt.name='英语-高中必修' THEN 1 ELSE 0 END,CASE WHEN lt.name='英语-高中必修' THEN 'required' ELSE 'selective' END,lt.name,@seed_now,@seed_now
FROM content_topic ct JOIN learning_topic lt ON lt.id=ct.topic_id JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id
WHERE lt.name IN ('英语-高中必修','英语-高中选择性必修') AND lc.content_type='word' AND lc.state='published'
ON DUPLICATE KEY UPDATE importance=VALUES(importance),is_core=VALUES(is_core),source_level=VALUES(source_level),source_ref=VALUES(source_ref),updated_at=@seed_now;

-- 旧数据可能只有 learning_content.stage 而尚未建立 content_topic，按阶段补全 K12 词书成员。
INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:PRIMARY:',lc.id)),MD5('vocabulary-book:PRIMARY'),lc.id,0,0,1,'stage','primary',@seed_now,@seed_now
FROM learning_content lc WHERE lc.content_type='word' AND lc.state='published' AND lc.stage='primary'
ON DUPLICATE KEY UPDATE updated_at=@seed_now;

INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:JUNIOR:',lc.id)),MD5('vocabulary-book:JUNIOR'),lc.id,0,0,1,'stage','junior',@seed_now,@seed_now
FROM learning_content lc WHERE lc.content_type='word' AND lc.state='published' AND lc.stage='junior'
ON DUPLICATE KEY UPDATE updated_at=@seed_now;

INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref,created_at,updated_at)
SELECT MD5(CONCAT('vbw:SENIOR:',lc.id)),MD5('vocabulary-book:SENIOR'),lc.id,0,0,1,'stage','senior',@seed_now,@seed_now
FROM learning_content lc WHERE lc.content_type='word' AND lc.state='published' AND lc.stage='senior'
ON DUPLICATE KEY UPDATE updated_at=@seed_now;

UPDATE vocabulary_book vb SET word_count=(SELECT COUNT(*) FROM vocabulary_book_word vbw WHERE vbw.book_id=vb.id)
WHERE book_code IN ('PRIMARY','JUNIOR','SENIOR');

COMMIT;
