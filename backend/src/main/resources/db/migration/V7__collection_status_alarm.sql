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

CALL add_column_if_missing('iot_comm_device', 'collect_interval_ms', 'collect_interval_ms INT NOT NULL DEFAULT 1000 COMMENT ''Collect interval in milliseconds'' AFTER status');
CALL add_column_if_missing('iot_comm_device', 'last_collect_time', 'last_collect_time BIGINT DEFAULT NULL COMMENT ''Last collect timestamp'' AFTER collect_interval_ms');
CALL add_column_if_missing('iot_comm_device', 'last_success_time', 'last_success_time BIGINT DEFAULT NULL COMMENT ''Last successful collect timestamp'' AFTER last_collect_time');
CALL add_column_if_missing('iot_comm_device', 'last_error_message', 'last_error_message VARCHAR(500) DEFAULT NULL COMMENT ''Last collect error message'' AFTER last_success_time');
CALL add_column_if_missing('iot_comm_device', 'fail_count', 'fail_count INT NOT NULL DEFAULT 0 COMMENT ''Consecutive failure count'' AFTER last_error_message');

DROP PROCEDURE IF EXISTS add_column_if_missing;

CREATE TABLE IF NOT EXISTS iot_alarm_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT DEFAULT NULL,
    device_id BIGINT DEFAULT NULL,
    point_id BIGINT DEFAULT NULL,
    protocol_type VARCHAR(40) DEFAULT NULL,
    alarm_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARN',
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    first_time BIGINT NOT NULL,
    last_time BIGINT NOT NULL,
    recover_time BIGINT DEFAULT NULL,
    occur_count INT NOT NULL DEFAULT 1,
    source_node VARCHAR(80) DEFAULT NULL,
    work_order_id BIGINT DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_alarm_status_time (status, last_time),
    KEY idx_alarm_device_status (device_id, status),
    KEY idx_alarm_project_status (project_id, status),
    KEY idx_alarm_work_order (work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Unified alarm event';

