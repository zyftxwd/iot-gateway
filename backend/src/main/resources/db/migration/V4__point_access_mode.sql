ALTER TABLE iot_comm_point
    ADD COLUMN access_mode VARCHAR(20) NOT NULL DEFAULT 'READ_ONLY' COMMENT 'READ_ONLY or READ_WRITE' AFTER enabled;

UPDATE iot_comm_point
SET access_mode = 'READ_WRITE'
WHERE LOWER(IFNULL(remark, '')) LIKE '%write%';

UPDATE iot_comm_point
SET point_key = CONCAT('point_', id)
WHERE point_key IS NULL OR TRIM(point_key) = '';

