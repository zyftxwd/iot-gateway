CREATE DATABASE IF NOT EXISTS iiot_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE iiot_db;

CREATE TABLE IF NOT EXISTS sys_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    nick_name VARCHAR(64) DEFAULT NULL,
    password VARCHAR(128) NOT NULL,
    dept_id BIGINT DEFAULT NULL,
    role_key VARCHAR(50) NOT NULL DEFAULT 'viewer',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system user';

CREATE TABLE IF NOT EXISTS iot_comm_device (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_name VARCHAR(100) NOT NULL,
    protocol_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(128) DEFAULT NULL,
    port INT DEFAULT NULL,
    ext_config TEXT DEFAULT NULL,
    remark VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_device_protocol (protocol_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='communication device';

CREATE TABLE IF NOT EXISTS iot_comm_point (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comm_device_id BIGINT NOT NULL,
    point_label VARCHAR(100) NOT NULL,
    point_key VARCHAR(128) NOT NULL,
    address VARCHAR(255) NOT NULL,
    data_type VARCHAR(50) NOT NULL DEFAULT 'Float32',
    coef DOUBLE NOT NULL DEFAULT 1,
    unit VARCHAR(30) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_device_key (comm_device_id, point_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='communication point';

CREATE TABLE IF NOT EXISTS iot_report_scheme (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL,
    report_type VARCHAR(50) NOT NULL,
    scheme_name VARCHAR(100) NOT NULL,
    filters_json TEXT DEFAULT NULL,
    layout_json TEXT DEFAULT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    create_time BIGINT DEFAULT NULL,
    update_time BIGINT DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_report_scheme_user_type (user_id, report_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='report scheme';
