-- V3.16.3 app_parameter 管理端与软删除。
-- 本脚本只增加删除标记；不得对 app_parameter 执行物理 DELETE。
ALTER TABLE app_parameter
  ADD COLUMN del_is TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=正常，1=软删除' AFTER state;

CREATE INDEX idx_app_parameter_visible ON app_parameter (del_is, state, param_key);
