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

CALL add_column_if_missing('iot_comm_point', 'history_enabled', 'history_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''Whether to store point history'' AFTER access_mode');
CALL add_column_if_missing('iot_comm_point', 'history_mode', 'history_mode VARCHAR(30) NOT NULL DEFAULT ''INHERIT'' COMMENT ''History strategy: INHERIT, DISABLED, INTERVAL, CHANGE, INTERVAL_CHANGE'' AFTER history_enabled');
CALL add_column_if_missing('iot_comm_point', 'history_interval_ms', 'history_interval_ms INT NOT NULL DEFAULT 300000 COMMENT ''Minimum history storage interval in milliseconds'' AFTER history_mode');
CALL add_column_if_missing('iot_comm_point', 'change_threshold', 'change_threshold DECIMAL(20,6) DEFAULT NULL COMMENT ''Store immediately when numeric change reaches threshold'' AFTER history_interval_ms');
CALL add_column_if_missing('iot_comm_point', 'store_on_change', 'store_on_change TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''Store immediately on value change'' AFTER change_threshold');
CALL add_column_if_missing('iot_comm_device', 'history_enabled', 'history_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''Whether to store point history for this device by default'' AFTER collect_interval_ms');
CALL add_column_if_missing('iot_comm_device', 'history_mode', 'history_mode VARCHAR(30) NOT NULL DEFAULT ''INTERVAL_CHANGE'' COMMENT ''Default point history strategy'' AFTER history_enabled');
CALL add_column_if_missing('iot_comm_device', 'history_interval_ms', 'history_interval_ms INT NOT NULL DEFAULT 300000 COMMENT ''Default point history interval in milliseconds'' AFTER history_mode');
CALL add_column_if_missing('iot_comm_device', 'change_threshold', 'change_threshold DECIMAL(20,6) DEFAULT NULL COMMENT ''Default point change threshold'' AFTER history_interval_ms');
CALL add_column_if_missing('iot_comm_device', 'store_on_change', 'store_on_change TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''Default store immediately on value change'' AFTER change_threshold');

DROP PROCEDURE IF EXISTS add_column_if_missing;

UPDATE iot_comm_point
SET history_enabled = 1
WHERE history_enabled IS NULL;

UPDATE iot_comm_point
SET history_mode = 'INHERIT'
WHERE history_mode IS NULL OR history_mode = '';

UPDATE iot_comm_point
SET history_interval_ms = 300000
WHERE history_interval_ms IS NULL OR history_interval_ms < 1000;

UPDATE iot_comm_point
SET store_on_change = 1
WHERE store_on_change IS NULL;

UPDATE iot_comm_device
SET history_enabled = 1
WHERE history_enabled IS NULL;

UPDATE iot_comm_device
SET history_mode = 'INTERVAL_CHANGE'
WHERE history_mode IS NULL OR history_mode = '';

UPDATE iot_comm_device
SET history_interval_ms = 300000
WHERE history_interval_ms IS NULL OR history_interval_ms < 1000;

UPDATE iot_comm_device
SET store_on_change = 1
WHERE store_on_change IS NULL;

