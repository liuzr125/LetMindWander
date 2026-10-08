-- V3.23：英语短文可编辑主适用词书。
-- english_article_book 保留每日生成日期、槽位和证据快照，不承担人工分类职责。

CREATE TABLE IF NOT EXISTS english_article_category (
  content_id CHAR(32) NOT NULL COMMENT 'learning_content.id；每篇短文最多一个主分类',
  book_id CHAR(32) NULL COMMENT 'vocabulary_book.id；NULL 且 del_is=1 表示管理员明确设为未分类',
  assigned_by CHAR(32) NULL COMMENT '最后设置分类的管理员主体',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发与变更版本',
  del_is TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删除：0 已分类，1 未分类',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (content_id),
  KEY idx_article_category_book (book_id,del_is,content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='英语短文人工可编辑的主适用词书分类';

-- 对既有每日生成短文建立初始主分类。若历史上存在多本词书映射，按词书排序取第一本；
-- 已存在人工分类（含软删除的“未分类”标记）时不覆盖，脚本可安全重跑。
INSERT INTO english_article_category
  (content_id,book_id,assigned_by,row_version,del_is,created_at,updated_at)
SELECT DISTINCT eab.content_id,eab.book_id,NULL,1,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)
FROM english_article_book eab
JOIN vocabulary_book vb ON vb.id=eab.book_id
WHERE NOT EXISTS (
  SELECT 1 FROM english_article_category existing
  WHERE existing.content_id=eab.content_id
)
AND NOT EXISTS (
  SELECT 1
  FROM english_article_book earlier
  JOIN vocabulary_book earlier_book ON earlier_book.id=earlier.book_id
  WHERE earlier.content_id=eab.content_id
    AND (earlier_book.sort_no<vb.sort_no OR (earlier_book.sort_no=vb.sort_no AND earlier.book_id<eab.book_id))
);
