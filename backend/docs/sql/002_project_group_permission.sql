USE iiot_db;

CREATE TABLE IF NOT EXISTS iot_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_name VARCHAR(100) NOT NULL COMMENT 'project name',
    project_code VARCHAR(64) NOT NULL COMMENT 'project code',
    owner_name VARCHAR(64) DEFAULT NULL COMMENT 'owner',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, PAUSED, ARCHIVED',
    remark VARCHAR(255) DEFAULT NULL COMMENT 'remark',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='industrial project';

CREATE TABLE IF NOT EXISTS iot_project_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT 'project id',
    parent_id BIGINT DEFAULT 0 COMMENT 'parent group id, 0 means project root',
    group_name VARCHAR(100) NOT NULL COMMENT 'group name',
    group_type VARCHAR(30) NOT NULL DEFAULT 'AREA' COMMENT 'AREA, LINE, STATION, SYSTEM',
    sort_no INT NOT NULL DEFAULT 0 COMMENT 'sort number',
    remark VARCHAR(255) DEFAULT NULL COMMENT 'remark',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_group_project_parent (project_id, parent_id),
    KEY idx_group_project_sort (project_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project group tree';

CREATE TABLE IF NOT EXISTS sys_user_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT 'user id',
    project_id BIGINT NOT NULL COMMENT 'project id',
    permission_level VARCHAR(30) NOT NULL DEFAULT 'VIEW' COMMENT 'VIEW, OPERATE, ADMIN',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_project (user_id, project_id),
    KEY idx_project_user (project_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user project permission';

ALTER TABLE iot_comm_device
    ADD COLUMN project_id BIGINT DEFAULT NULL COMMENT 'project id' AFTER id,
    ADD COLUMN group_id BIGINT DEFAULT NULL COMMENT 'group id' AFTER project_id,
    ADD COLUMN device_type VARCHAR(50) DEFAULT NULL COMMENT 'device type' AFTER device_name,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'ONLINE, OFFLINE, UNKNOWN' AFTER port;

CREATE INDEX idx_device_project_group ON iot_comm_device (project_id, group_id);
CREATE INDEX idx_device_project_protocol ON iot_comm_device (project_id, protocol_type);

INSERT INTO iot_project (project_name, project_code, owner_name, remark)
SELECT 'Default Project', 'DEFAULT', 'Admin', 'Auto created default project'
WHERE NOT EXISTS (SELECT 1 FROM iot_project WHERE project_code = 'DEFAULT');

INSERT INTO iot_project_group (project_id, parent_id, group_name, group_type, sort_no, remark)
SELECT p.id, 0, 'Default Area', 'AREA', 1, 'Auto created default area'
FROM iot_project p
WHERE p.project_code = 'DEFAULT'
  AND NOT EXISTS (
      SELECT 1 FROM iot_project_group g WHERE g.project_id = p.id AND g.group_name = 'Default Area'
  );

UPDATE iot_comm_device d
JOIN iot_project p ON p.project_code = 'DEFAULT'
JOIN iot_project_group g ON g.project_id = p.id AND g.group_name = 'Default Area'
SET d.project_id = p.id,
    d.group_id = g.id,
    d.device_type = IFNULL(d.device_type, 'GENERAL'),
    d.status = IFNULL(d.status, 'UNKNOWN')
WHERE d.project_id IS NULL;
