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

CREATE TABLE IF NOT EXISTS iot_alarm_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    rule_name VARCHAR(120) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARN',
    condition_type VARCHAR(30) NOT NULL,
    threshold_value DECIMAL(20,6) DEFAULT NULL,
    threshold_high DECIMAL(20,6) DEFAULT NULL,
    recover_value DECIMAL(20,6) DEFAULT NULL,
    recover_high DECIMAL(20,6) DEFAULT NULL,
    immediate_alarm TINYINT(1) NOT NULL DEFAULT 0,
    trigger_duration_ms INT NOT NULL DEFAULT 60000,
    recover_duration_ms INT NOT NULL DEFAULT 300000,
    remark VARCHAR(255) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_rule_point_enabled (point_id, enabled),
    KEY idx_rule_device_enabled (device_id, enabled),
    KEY idx_rule_project_enabled (project_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Point alarm rules';

CALL add_column_if_missing('iot_alarm_event', 'alarm_rule_id', 'alarm_rule_id BIGINT DEFAULT NULL COMMENT ''Alarm rule id'' AFTER point_id');
CALL add_column_if_missing('iot_alarm_event', 'current_value', 'current_value VARCHAR(100) DEFAULT NULL COMMENT ''Current trigger value'' AFTER message');
CALL add_column_if_missing('iot_alarm_event', 'threshold_text', 'threshold_text VARCHAR(200) DEFAULT NULL COMMENT ''Alarm threshold description'' AFTER current_value');

DROP PROCEDURE IF EXISTS add_column_if_missing;

