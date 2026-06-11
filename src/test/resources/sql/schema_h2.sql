-- H2 测试库全量建表（MySQL 兼容模式）

CREATE TABLE IF NOT EXISTS NTC_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(64) NOT NULL,
    account VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    CONSTRAINT uk_ntc_user_account UNIQUE (account)
);

MERGE INTO NTC_user (id, user_name, account, password_hash, status, is_deleted, created_by, updated_by)
KEY (id)
VALUES (1, 'System Admin', 'admin', '{noop}1233321', 1, 0, 'test', 'test');

CREATE TABLE IF NOT EXISTS t_script_work (
    id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL DEFAULT '',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_character (
    id VARCHAR(64) NOT NULL,
    work_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) DEFAULT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    personality VARCHAR(1000) DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_script_record (
    id BIGINT NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    work_id VARCHAR(64) DEFAULT NULL,
    work_title VARCHAR(256) NOT NULL DEFAULT '',
    chapter_number INT NOT NULL,
    chapter_content CLOB NOT NULL,
    chapter_content_hash VARCHAR(64) DEFAULT NULL,
    script_content CLOB NOT NULL,
    model_name VARCHAR(128) DEFAULT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    generation_id VARCHAR(64) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_t_script_record_user_work_chapter UNIQUE (user_id, work_title, chapter_number)
);

CREATE TABLE IF NOT EXISTS t_script_message (
    id VARCHAR(64) NOT NULL,
    work_id VARCHAR(64) NOT NULL,
    chapter_number INT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content CLOB NOT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_t_script_message_work_chapter (work_id, chapter_number)
);

CREATE TABLE IF NOT EXISTS t_llm_trace_run (
    id BIGINT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    trace_name VARCHAR(128) NOT NULL,
    entry_method VARCHAR(256) NOT NULL,
    conversation_id VARCHAR(64) DEFAULT NULL,
    task_id VARCHAR(64) DEFAULT NULL,
    user_id VARCHAR(64) DEFAULT NULL,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    extra_data CLOB DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_t_llm_trace_run_trace_id UNIQUE (trace_id)
);

CREATE TABLE IF NOT EXISTS t_llm_trace_node (
    id BIGINT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    parent_node_id VARCHAR(64) DEFAULT NULL,
    depth INT NOT NULL DEFAULT 0,
    node_type VARCHAR(32) NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    class_name VARCHAR(256) NOT NULL,
    method_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    extra_data CLOB DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_t_llm_trace_node_trace_node UNIQUE (trace_id, node_id)
);
