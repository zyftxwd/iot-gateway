CREATE TABLE IF NOT EXISTS iot_point_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT DEFAULT NULL,
    device_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    point_key VARCHAR(100) NOT NULL,
    point_label VARCHAR(100) DEFAULT NULL,
    protocol_type VARCHAR(40) DEFAULT NULL,
    value_text VARCHAR(100) DEFAULT NULL,
    value_number DECIMAL(20,6) DEFAULT NULL,
    raw_value VARCHAR(200) DEFAULT NULL,
    quality VARCHAR(20) NOT NULL DEFAULT 'GOOD',
    collect_time BIGINT NOT NULL,
    collect_cost_ms BIGINT DEFAULT NULL,
    collector_node VARCHAR(80) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_history_point_time (point_id, collect_time),
    KEY idx_history_device_time (device_id, collect_time),
    KEY idx_history_project_time (project_id, collect_time),
    KEY idx_history_key_time (point_key, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Point history values';

