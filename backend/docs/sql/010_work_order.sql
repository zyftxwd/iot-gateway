USE iiot_db;

CREATE TABLE IF NOT EXISTS iot_work_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    alarm_event_id BIGINT DEFAULT NULL,
    project_id BIGINT DEFAULT NULL,
    device_id BIGINT DEFAULT NULL,
    point_id BIGINT DEFAULT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    flow_key VARCHAR(60) NOT NULL DEFAULT 'STANDARD_MAINTENANCE',
    creator_user_id BIGINT DEFAULT NULL,
    creator_name VARCHAR(80) DEFAULT NULL,
    assignee_user_id BIGINT DEFAULT NULL,
    assignee_name VARCHAR(80) DEFAULT NULL,
    dept_id BIGINT DEFAULT NULL,
    create_time BIGINT NOT NULL,
    dispatch_time BIGINT DEFAULT NULL,
    accept_time BIGINT DEFAULT NULL,
    process_time BIGINT DEFAULT NULL,
    finish_time BIGINT DEFAULT NULL,
    verify_time BIGINT DEFAULT NULL,
    close_time BIGINT DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_order_no (order_no),
    KEY idx_work_order_status_time (status, create_time),
    KEY idx_work_order_project_status (project_id, status),
    KEY idx_work_order_device_status (device_id, status),
    KEY idx_work_order_alarm (alarm_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Work order for alarm and maintenance closure';

DROP PROCEDURE IF EXISTS add_column_if_missing;

DELIMITER $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_sql VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_sql);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('iot_work_order', 'verifier_user_id', 'verifier_user_id BIGINT DEFAULT NULL COMMENT ''Verifier user id'' AFTER assignee_name');
CALL add_column_if_missing('iot_work_order', 'verifier_name', 'verifier_name VARCHAR(80) DEFAULT NULL COMMENT ''Verifier name'' AFTER verifier_user_id');
CALL add_column_if_missing('iot_work_order', 'planned_finish_time', 'planned_finish_time BIGINT DEFAULT NULL COMMENT ''Planned finish timestamp'' AFTER dept_id');
CALL add_column_if_missing('iot_work_order', 'fault_type', 'fault_type VARCHAR(80) DEFAULT NULL COMMENT ''Fault type'' AFTER description');
CALL add_column_if_missing('iot_work_order', 'fault_reason', 'fault_reason VARCHAR(1000) DEFAULT NULL COMMENT ''Fault reason'' AFTER fault_type');
CALL add_column_if_missing('iot_work_order', 'process_measure', 'process_measure VARCHAR(1000) DEFAULT NULL COMMENT ''Process measure'' AFTER fault_reason');
CALL add_column_if_missing('iot_work_order', 'process_result', 'process_result VARCHAR(1000) DEFAULT NULL COMMENT ''Process result'' AFTER process_measure');
CALL add_column_if_missing('iot_work_order', 'archive_card_id', 'archive_card_id BIGINT DEFAULT NULL COMMENT ''Generated maintenance card id'' AFTER close_time');

DROP PROCEDURE IF EXISTS add_column_if_missing;

CREATE TABLE IF NOT EXISTS iot_project_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_key VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_user_role (project_id, user_id, role_key),
    KEY idx_project_member_role (project_id, role_key, status),
    KEY idx_project_member_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Project member role for work order';

CREATE TABLE IF NOT EXISTS iot_work_order_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    auto_create_from_alarm TINYINT(1) NOT NULL DEFAULT 0,
    allow_dispatcher_as_assignee TINYINT(1) NOT NULL DEFAULT 0,
    allow_dispatcher_as_verifier TINYINT(1) NOT NULL DEFAULT 1,
    allow_assignee_verify_self TINYINT(1) NOT NULL DEFAULT 0,
    auto_close_after_verify TINYINT(1) NOT NULL DEFAULT 1,
    auto_archive_after_close TINYINT(1) NOT NULL DEFAULT 1,
    require_process_photo TINYINT(1) NOT NULL DEFAULT 0,
    require_fault_reason TINYINT(1) NOT NULL DEFAULT 1,
    require_process_measure TINYINT(1) NOT NULL DEFAULT 1,
    accept_timeout_minutes INT DEFAULT NULL,
    finish_timeout_minutes INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_order_policy_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Project work order policy';

CREATE TABLE IF NOT EXISTS iot_work_order_participant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    username VARCHAR(80) DEFAULT NULL,
    participant_role VARCHAR(30) NOT NULL,
    create_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_order_user_role (work_order_id, user_id, participant_role),
    KEY idx_work_order_participant_order (work_order_id),
    KEY idx_work_order_participant_user (user_id, participant_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Work order participants';

CREATE TABLE IF NOT EXISTS iot_work_order_flow_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    from_status VARCHAR(30) DEFAULT NULL,
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    operator_user_id BIGINT DEFAULT NULL,
    operator_name VARCHAR(80) DEFAULT NULL,
    remark VARCHAR(1000) DEFAULT NULL,
    action_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_work_order_flow_order (work_order_id, action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Work order flow log';

CREATE TABLE IF NOT EXISTS iot_work_order_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    attachment_type VARCHAR(30) NOT NULL DEFAULT 'FILE',
    file_name VARCHAR(200) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_size BIGINT DEFAULT NULL,
    uploader_user_id BIGINT DEFAULT NULL,
    uploader_name VARCHAR(80) DEFAULT NULL,
    upload_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_work_order_attachment_order (work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Work order attachments';

CREATE TABLE IF NOT EXISTS iot_maintenance_card (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    alarm_event_id BIGINT DEFAULT NULL,
    project_id BIGINT DEFAULT NULL,
    device_id BIGINT DEFAULT NULL,
    point_id BIGINT DEFAULT NULL,
    title VARCHAR(160) NOT NULL,
    fault_type VARCHAR(80) DEFAULT NULL,
    fault_reason VARCHAR(1000) DEFAULT NULL,
    process_measure VARCHAR(1000) DEFAULT NULL,
    process_result VARCHAR(1000) DEFAULT NULL,
    keywords VARCHAR(500) DEFAULT NULL,
    tags VARCHAR(500) DEFAULT NULL,
    creator_user_id BIGINT DEFAULT NULL,
    creator_name VARCHAR(80) DEFAULT NULL,
    create_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_maintenance_card_work_order (work_order_id),
    KEY idx_maintenance_card_device_point (device_id, point_id),
    KEY idx_maintenance_card_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Maintenance knowledge card generated from work order';
