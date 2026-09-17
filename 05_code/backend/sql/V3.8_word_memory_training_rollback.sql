-- V3.8 英语记忆训练回滚 / MySQL 5.7.25
-- 警告：以下操作会删除用户的训练会话、作答和证据。仅在功能开关关闭、数据完成导出备份后执行。
SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS word_memory_evidence;
DROP TABLE IF EXISTS word_memory_attempt;
DROP TABLE IF EXISTS word_memory_hint_event;
DROP TABLE IF EXISTS word_memory_episode;
DROP TABLE IF EXISTS word_memory_session;
DROP TABLE IF EXISTS word_memory_question;
DROP TABLE IF EXISTS word_memory_hint;
SET FOREIGN_KEY_CHECKS=1;
