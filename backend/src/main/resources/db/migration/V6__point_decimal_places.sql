ALTER TABLE iot_comm_point
    ADD COLUMN decimal_places INT NOT NULL DEFAULT 2 COMMENT '显示小数位数' AFTER coef;

UPDATE iot_comm_point
SET decimal_places = 2
WHERE decimal_places IS NULL;

