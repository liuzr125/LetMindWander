-- “我的”及二级页面数据库结构核验（MySQL 5.7/8.0）
--
-- 结论：V3.0 数据字典已包含本功能需要的表和字段，本次不执行 DROP/CREATE/ALTER。
-- 该脚本只报告缺失项，不修改生产数据。若查询结果为空，表示结构满足要求。

-- 1. 缺失的数据表
SELECT required.table_name AS missing_table
FROM (
    SELECT 'app_user' AS table_name
    UNION ALL SELECT 'user_favorite'
    UNION ALL SELECT 'friend_relation'
    UNION ALL SELECT 'friend_request'
    UNION ALL SELECT 'user_inbox_event'
    UNION ALL SELECT 'knowledge_share_rule'
    UNION ALL SELECT 'resource_schedule'
    UNION ALL SELECT 'resource_schedule_source'
    UNION ALL SELECT 'resource_run'
    UNION ALL SELECT 'user_feedback'
    UNION ALL SELECT 'data_export'
    UNION ALL SELECT 'data_deletion'
    UNION ALL SELECT 'ai_daily_quota'
) required
LEFT JOIN information_schema.tables actual
    ON actual.table_schema = DATABASE()
   AND actual.table_name = required.table_name
WHERE actual.table_name IS NULL
ORDER BY required.table_name;

-- 2. 本次功能依赖但可能因旧库版本而缺失的字段
SELECT required.table_name, required.column_name
FROM (
    SELECT 'app_user' AS table_name, 'nickname' AS column_name
    UNION ALL SELECT 'app_user', 'avatar_url'
    UNION ALL SELECT 'app_user', 'short_id'
    UNION ALL SELECT 'app_user', 'real_name'
    UNION ALL SELECT 'app_user', 'english_name'
    UNION ALL SELECT 'app_user', 'gender'
    UNION ALL SELECT 'app_user', 'birthday'
    UNION ALL SELECT 'app_user', 'hobbies_json'
    UNION ALL SELECT 'app_user', 'introduction'
    UNION ALL SELECT 'app_user', 'profile_visibility'
    UNION ALL SELECT 'app_user', 'ai_consent_version'
    UNION ALL SELECT 'app_user', 'ai_consented_at'
    UNION ALL SELECT 'app_user', 'row_version'
    UNION ALL SELECT 'resource_schedule', 'topics_json'
    UNION ALL SELECT 'resource_schedule', 'dedup_enabled'
    UNION ALL SELECT 'resource_schedule', 'summary_enabled'
    UNION ALL SELECT 'resource_schedule', 'version_no'
) required
LEFT JOIN information_schema.columns actual
    ON actual.table_schema = DATABASE()
   AND actual.table_name = required.table_name
   AND actual.column_name = required.column_name
WHERE actual.column_name IS NULL
ORDER BY required.table_name, required.column_name;
