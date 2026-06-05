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

CALL add_column_if_missing('sys_user', 'failed_login_count', 'failed_login_count INT NOT NULL DEFAULT 0 COMMENT ''连续登录失败次数'' AFTER status');
CALL add_column_if_missing('sys_user', 'lock_until', 'lock_until BIGINT DEFAULT NULL COMMENT ''账号锁定到期时间戳，毫秒'' AFTER failed_login_count');
CALL add_column_if_missing('sys_user', 'last_login_time', 'last_login_time BIGINT DEFAULT NULL COMMENT ''最后登录时间戳，毫秒'' AFTER lock_until');
CALL add_column_if_missing('sys_user', 'password_changed_time', 'password_changed_time BIGINT DEFAULT NULL COMMENT ''密码最后修改时间戳，毫秒'' AFTER last_login_time');

DROP PROCEDURE IF EXISTS add_column_if_missing;

UPDATE sys_user
SET failed_login_count = 0
WHERE failed_login_count IS NULL;

