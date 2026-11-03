-- English illustrated reading books. Additive, repeatable MySQL 5.7 migration.
-- Apply on a backed-up staging database before production; no automatic production DDL.
CREATE TABLE IF NOT EXISTS english_reading_book (
 id CHAR(32) NOT NULL PRIMARY KEY,
 title VARCHAR(240) NOT NULL,
 draft_json LONGTEXT NOT NULL,
 published_title VARCHAR(240) NULL,
 difficulty VARCHAR(24) NOT NULL DEFAULT 'intro', published_difficulty VARCHAR(24) NULL,
 published_json LONGTEXT NULL,
 revision INT NOT NULL DEFAULT 1,
 published_revision INT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'draft',
 created_by CHAR(32) NOT NULL,
 updated_by CHAR(32) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX idx_reading_book_state (state,updated_at)
);
CREATE TABLE IF NOT EXISTS english_book_job (
 id CHAR(32) NOT NULL PRIMARY KEY, book_id CHAR(32) NOT NULL,
 kind VARCHAR(24) NOT NULL, state VARCHAR(24) NOT NULL,
 worker_id CHAR(32) NULL, lease_until TIMESTAMP(3) NULL,
 options_json TEXT NOT NULL, message VARCHAR(500) NOT NULL DEFAULT '',
 completed_count INT NOT NULL DEFAULT 0, total_count INT NOT NULL DEFAULT 0, spent_units BIGINT NOT NULL DEFAULT 0,
 created_by CHAR(32) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX idx_book_job_queue (state,created_at), INDEX idx_book_job_book (book_id,created_at)
);
CREATE TABLE IF NOT EXISTS english_book_generation (
 step_key CHAR(64) NOT NULL PRIMARY KEY, book_id CHAR(32) NOT NULL,
 state VARCHAR(24) NOT NULL, payload LONGBLOB NULL, asset_id CHAR(32) NULL, duration_seconds DOUBLE NOT NULL DEFAULT 0,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE IF NOT EXISTS english_book_progress (
 owner_id CHAR(32) NOT NULL, book_id CHAR(32) NOT NULL,
 revision INT NOT NULL DEFAULT 0, progress_json LONGTEXT NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(owner_id,book_id)
);
CREATE TABLE IF NOT EXISTS english_book_word_source (
 owner_id CHAR(32) NOT NULL, book_id CHAR(32) NOT NULL,
 chapter_id CHAR(32) NOT NULL, paragraph_id CHAR(32) NOT NULL,
 content_id CHAR(32) NOT NULL, book_revision INT NOT NULL,
 quote_text TEXT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(owner_id,book_id,paragraph_id,content_id)
);
INSERT INTO admin_menu(id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000c7','english_books','英语书籍','/english-books','▤',55,1,
 (SELECT id FROM admin_menu WHERE code='group_english'),'完整英语图文书籍、音频生成与学习'
WHERE NOT EXISTS(SELECT 1 FROM admin_menu WHERE code='english_books');
INSERT INTO admin_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM admin_role r CROSS JOIN admin_menu m
WHERE r.code='SUPER_ADMIN' AND m.code='english_books'
 AND NOT EXISTS(SELECT 1 FROM admin_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id);

UPDATE admin_menu SET description='英语单词、词库批次、英语短文、英语书籍与词书学习记录' WHERE code='group_english';
