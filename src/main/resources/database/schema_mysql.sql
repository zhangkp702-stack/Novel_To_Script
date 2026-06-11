-- NovelToScript MySQL 全量建表脚本
-- 用途：新库初始化，直接执行本文件即可创建全部表与默认管理员账号
-- 用法：mysql -u root -p < src/main/resources/database/schema_mysql.sql

CREATE DATABASE IF NOT EXISTS NTS_user
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE NTS_user;

-- ---------------------------------------------------------------------------
-- 用户与鉴权
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS NTC_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_name VARCHAR(64) NOT NULL COMMENT '显示名称',
    account VARCHAR(64) NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希（{bcrypt}... 或 {noop}...）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识：0=正常，1=已删除',
    failed_login_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until DATETIME DEFAULT NULL COMMENT '锁定截止时间',
    last_login_at DATETIME DEFAULT NULL COMMENT '最近成功登录时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ntc_user_account (account),
    KEY idx_ntc_user_status_deleted (status, is_deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户表';

-- ---------------------------------------------------------------------------
-- 剧本业务
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS t_script_work (
    id VARCHAR(64) NOT NULL COMMENT '作品ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户账号',
    title VARCHAR(256) NOT NULL DEFAULT '' COMMENT '作品标题',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    KEY idx_t_script_work_user_id (user_id),
    KEY idx_t_script_work_update_time (update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '剧本作品';

CREATE TABLE IF NOT EXISTS t_character (
    id VARCHAR(64) NOT NULL COMMENT '人物ID',
    work_id VARCHAR(64) NOT NULL COMMENT '作品ID',
    name VARCHAR(128) NOT NULL COMMENT '剧本中使用的名称',
    display_name VARCHAR(128) DEFAULT NULL COMMENT '显示名称或别名',
    description VARCHAR(1000) DEFAULT NULL COMMENT '身份或背景描述',
    personality VARCHAR(1000) DEFAULT NULL COMMENT '性格特征',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    KEY idx_t_character_work_id (work_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '作品人物设定';

CREATE TABLE IF NOT EXISTS t_script_record (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户账号',
    work_id VARCHAR(64) DEFAULT NULL COMMENT '作品ID',
    work_title VARCHAR(256) NOT NULL DEFAULT '' COMMENT '作品标题',
    chapter_number INT NOT NULL COMMENT '章节编号',
    chapter_content TEXT NOT NULL COMMENT '原章节内容',
    chapter_content_hash VARCHAR(64) DEFAULT NULL COMMENT '原章节内容哈希',
    script_content TEXT NOT NULL COMMENT '生成剧本内容',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT 'LLM链路ID',
    generation_id VARCHAR(64) DEFAULT NULL COMMENT '单次生成任务ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_script_record_user_work_chapter (user_id, work_title, chapter_number),
    KEY idx_t_script_record_user_id (user_id),
    KEY idx_t_script_record_work_id (work_id),
    KEY idx_t_script_record_work_title (work_title),
    KEY idx_t_script_record_update_time (update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '剧本生成记录';

CREATE TABLE IF NOT EXISTS t_script_message (
    id VARCHAR(64) NOT NULL COMMENT '消息ID',
    work_id VARCHAR(64) NOT NULL COMMENT '作品ID',
    chapter_number INT NOT NULL COMMENT '章节编号',
    role VARCHAR(16) NOT NULL COMMENT '消息角色：system/user/assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT 'LLM链路ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    KEY idx_t_script_message_work_chapter (work_id, chapter_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '剧本改编对话消息';

-- ---------------------------------------------------------------------------
-- LLM Trace
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS t_llm_trace_run (
    id BIGINT NOT NULL COMMENT '主键ID',
    trace_id VARCHAR(64) NOT NULL COMMENT '全局链路ID',
    trace_name VARCHAR(128) NOT NULL COMMENT '链路名称',
    entry_method VARCHAR(256) NOT NULL COMMENT '入口方法',
    conversation_id VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    task_id VARCHAR(64) DEFAULT NULL COMMENT '任务ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/ERROR',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    start_time DATETIME(3) NOT NULL COMMENT '开始时间',
    end_time DATETIME(3) DEFAULT NULL COMMENT '结束时间',
    duration_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    extra_data TEXT DEFAULT NULL COMMENT '扩展信息JSON',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_llm_trace_run_trace_id (trace_id),
    KEY idx_t_llm_trace_run_task_id (task_id),
    KEY idx_t_llm_trace_run_user_id (user_id),
    KEY idx_t_llm_trace_run_start_time (start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'LLM链路运行日志';

CREATE TABLE IF NOT EXISTS t_llm_trace_node (
    id BIGINT NOT NULL COMMENT '主键ID',
    trace_id VARCHAR(64) NOT NULL COMMENT '全局链路ID',
    node_id VARCHAR(64) NOT NULL COMMENT '节点ID',
    parent_node_id VARCHAR(64) DEFAULT NULL COMMENT '父节点ID',
    depth INT NOT NULL DEFAULT 0 COMMENT '节点深度',
    node_type VARCHAR(32) NOT NULL COMMENT '节点类型',
    node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
    class_name VARCHAR(256) NOT NULL COMMENT '类名',
    method_name VARCHAR(128) NOT NULL COMMENT '方法名',
    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/ERROR',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    start_time DATETIME(3) NOT NULL COMMENT '开始时间',
    end_time DATETIME(3) DEFAULT NULL COMMENT '结束时间',
    duration_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    extra_data TEXT DEFAULT NULL COMMENT '扩展信息JSON',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_llm_trace_node_trace_node (trace_id, node_id),
    KEY idx_t_llm_trace_node_parent (trace_id, parent_node_id),
    KEY idx_t_llm_trace_node_start_time (start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'LLM链路节点日志';

-- ---------------------------------------------------------------------------
-- 默认数据
-- ---------------------------------------------------------------------------

INSERT INTO NTC_user (
    user_name,
    account,
    password_hash,
    status,
    is_deleted,
    created_by,
    updated_by
)
VALUES (
    'System Admin',
    'admin',
    '{noop}1233321',
    1,
    0,
    'system',
    'system'
)
ON DUPLICATE KEY UPDATE
    user_name = VALUES(user_name),
    status = VALUES(status),
    is_deleted = VALUES(is_deleted),
    updated_by = 'system';
