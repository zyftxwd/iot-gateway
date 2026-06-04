USE iiot_db;

ALTER TABLE sys_user ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';

UPDATE sys_user
SET status = 'ACTIVE'
WHERE status IS NULL OR status = '';
