USE iiot_db;

ALTER TABLE iot_comm_point
    ADD COLUMN function_code INT NOT NULL DEFAULT 3 COMMENT 'Modbus功能码: 1线圈,2离散输入,3保持寄存器,4输入寄存器' AFTER address,
    ADD COLUMN slave_id INT NOT NULL DEFAULT 1 COMMENT 'Modbus从站ID' AFTER function_code,
    ADD COLUMN quantity INT NOT NULL DEFAULT 1 COMMENT '读取数量: 寄存器或位数量' AFTER slave_id,
    ADD COLUMN byte_order VARCHAR(10) NOT NULL DEFAULT 'ABCD' COMMENT '四字节数据字节序: ABCD,BADC,CDAB,DCBA' AFTER data_type,
    ADD COLUMN word_order VARCHAR(10) NOT NULL DEFAULT 'AB' COMMENT '双寄存器字顺序: AB,BA' AFTER byte_order,
    ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用采集' AFTER unit,
    ADD COLUMN remark VARCHAR(255) DEFAULT NULL COMMENT '点位备注' AFTER enabled;

CREATE INDEX idx_point_device_enabled ON iot_comm_point (comm_device_id, enabled);
CREATE INDEX idx_point_modbus_group ON iot_comm_point (comm_device_id, slave_id, function_code, address);

UPDATE iot_comm_point
SET quantity = 2
WHERE UPPER(data_type) IN ('INT32', 'UINT32', 'FLOAT32') AND quantity = 1;
