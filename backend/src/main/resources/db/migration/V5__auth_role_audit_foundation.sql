CREATE TABLE IF NOT EXISTS sys_dept (
    dept_id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0,
    dept_name VARCHAR(80) NOT NULL,
    dept_type VARCHAR(30) DEFAULT 'DEPT',
    sort_no INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='department or team';

CREATE TABLE IF NOT EXISTS sys_role (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_key VARCHAR(40) NOT NULL,
    role_name VARCHAR(80) NOT NULL,
    role_scope VARCHAR(30) DEFAULT 'PROJECT',
    remark VARCHAR(255) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role definition';

CREATE TABLE IF NOT EXISTS sys_operation_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL,
    username VARCHAR(50) DEFAULT NULL,
    project_id BIGINT DEFAULT NULL,
    device_id BIGINT DEFAULT NULL,
    point_id BIGINT DEFAULT NULL,
    work_order_id BIGINT DEFAULT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_target VARCHAR(100) DEFAULT NULL,
    detail TEXT,
    result VARCHAR(20) DEFAULT 'SUCCESS',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_user_time (user_id, create_time),
    KEY idx_audit_project_time (project_id, create_time),
    KEY idx_audit_device_time (device_id, create_time),
    KEY idx_audit_work_order (work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='operation audit, reserved work order linkage';

INSERT INTO sys_dept (dept_id, parent_id, dept_name, dept_type, sort_no, status)
SELECT 1, 0, '系统管理部', 'DEPT', 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE dept_id = 1);

INSERT INTO sys_dept (dept_id, parent_id, dept_name, dept_type, sort_no, status)
SELECT 2, 0, '现场运维部', 'DEPT', 2, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE dept_id = 2);

UPDATE sys_dept SET dept_name = '系统管理部', dept_type = 'DEPT', sort_no = 1, status = 'ACTIVE' WHERE dept_id = 1;
UPDATE sys_dept SET dept_name = '现场运维部', dept_type = 'DEPT', sort_no = 2, status = 'ACTIVE' WHERE dept_id = 2;

INSERT INTO sys_role (role_key, role_name, role_scope, remark)
SELECT 'admin', '系统管理员', 'GLOBAL', '拥有系统全部功能'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'admin');

INSERT INTO sys_role (role_key, role_name, role_scope, remark)
SELECT 'operator', '运维操作员', 'PROJECT', '可操作被授权项目内的设备和可写点位'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'operator');

INSERT INTO sys_role (role_key, role_name, role_scope, remark)
SELECT 'viewer', '只读查看员', 'PROJECT', '只能查看被授权项目'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'viewer');

UPDATE sys_role SET role_name = '系统管理员', role_scope = 'GLOBAL', remark = '拥有系统全部功能' WHERE role_key = 'admin';
UPDATE sys_role SET role_name = '运维操作员', role_scope = 'PROJECT', remark = '可操作被授权项目内的设备和可写点位' WHERE role_key = 'operator';
UPDATE sys_role SET role_name = '只读查看员', role_scope = 'PROJECT', remark = '只能查看被授权项目' WHERE role_key = 'viewer';

INSERT INTO sys_user (username, nick_name, dept_id, role_key, password)
SELECT 'admin', '系统管理员', 1, 'admin', '123456'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_user (username, nick_name, dept_id, role_key, password)
SELECT 'li_shifu', '李师傅', 2, 'operator', '123456'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'li_shifu');

UPDATE sys_user SET nick_name = '系统管理员', dept_id = 1, role_key = 'admin' WHERE username = 'admin';
UPDATE sys_user SET nick_name = '李师傅', dept_id = 2, role_key = 'operator' WHERE username = 'li_shifu';

