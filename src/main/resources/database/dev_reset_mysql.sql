-- 开发环境重置：删除业务表（保留 NTC_user 登录账号）
-- 执行顺序：
--   1) mysql -u root -p < src/main/resources/database/dev_reset_mysql.sql
--   2) mysql -u root -p < src/main/resources/database/schema_mysql.sql

USE NTS_user;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS t_script_message;
DROP TABLE IF EXISTS t_script_record;
DROP TABLE IF EXISTS t_character;
DROP TABLE IF EXISTS t_script_work;
DROP TABLE IF EXISTS t_llm_trace_node;
DROP TABLE IF EXISTS t_llm_trace_run;

SET FOREIGN_KEY_CHECKS = 1;
