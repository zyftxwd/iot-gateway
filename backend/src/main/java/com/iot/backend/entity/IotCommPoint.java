package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通讯点表配置。
 * 一行点表代表某台设备上的一个采集点，例如主轴转速、温度、压力。
 */
@Data
@TableName("iot_comm_point")
public class IotCommPoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 iot_comm_device.id。 */
    private Long commDeviceId;

    /** 展示名称，例如“主轴转速”。 */
    private String pointLabel;

    /** 业务唯一 key，例如 spindle_speed，采集结果会用它作为 JSON 字段名。 */
    private String pointKey;

    /** Modbus 地址，例如 40001。 */
    private String address;

    /** Modbus 功能码：1线圈，2离散输入，3保持寄存器，4输入寄存器。 */
    private Integer functionCode;

    /** Modbus 从站 ID。 */
    private Integer slaveId;

    /** 读取数量，寄存器点位通常为 1 或 2，线圈点位通常为 1。 */
    private Integer quantity;

    /** 数据类型：Boolean、Int16、UInt16、Int32、UInt32、Float32。 */
    private String dataType;

    /** 四字节数据字节序：ABCD、BADC、CDAB、DCBA。 */
    private String byteOrder;

    /** 双寄存器字顺序：AB、BA。byteOrder 为空时可用它推导。 */
    private String wordOrder;

    /** 工程量换算倍率。 */
    private Double coef;

    /** 数值显示小数位数，默认 2。 */
    private Integer decimalPlaces;

    /** 单位，例如 rpm、℃、MPa。 */
    private String unit;

    /** 是否启用采集。 */
    private Boolean enabled;

    /** 点位访问模式：READ_ONLY 或 READ_WRITE。 */
    private String accessMode;

    /** 是否存储历史数据。 */
    private Boolean historyEnabled;

    /** 历史存储策略：DISABLED、INTERVAL、CHANGE、INTERVAL_CHANGE。 */
    private String historyMode;

    /** 历史数据最小存储间隔，单位毫秒。 */
    private Integer historyIntervalMs;

    /** 数值变化达到该阈值时立即存储。 */
    private java.math.BigDecimal changeThreshold;

    /** 是否在值变化时立即存储。 */
    private Boolean storeOnChange;

    /** 点位备注。 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCommDeviceId() {
        return commDeviceId;
    }

    public void setCommDeviceId(Long commDeviceId) {
        this.commDeviceId = commDeviceId;
    }

    public String getPointLabel() {
        return pointLabel;
    }

    public void setPointLabel(String pointLabel) {
        this.pointLabel = pointLabel;
    }

    public String getPointKey() {
        return pointKey;
    }

    public void setPointKey(String pointKey) {
        this.pointKey = pointKey;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getFunctionCode() {
        return functionCode;
    }

    public void setFunctionCode(Integer functionCode) {
        this.functionCode = functionCode;
    }

    public Integer getSlaveId() {
        return slaveId;
    }

    public void setSlaveId(Integer slaveId) {
        this.slaveId = slaveId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getByteOrder() {
        return byteOrder;
    }

    public void setByteOrder(String byteOrder) {
        this.byteOrder = byteOrder;
    }

    public String getWordOrder() {
        return wordOrder;
    }

    public void setWordOrder(String wordOrder) {
        this.wordOrder = wordOrder;
    }

    public Double getCoef() {
        return coef;
    }

    public void setCoef(Double coef) {
        this.coef = coef;
    }

    public Integer getDecimalPlaces() {
        return decimalPlaces;
    }

    public void setDecimalPlaces(Integer decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }

    public Boolean getHistoryEnabled() {
        return historyEnabled;
    }

    public void setHistoryEnabled(Boolean historyEnabled) {
        this.historyEnabled = historyEnabled;
    }

    public String getHistoryMode() {
        return historyMode;
    }

    public void setHistoryMode(String historyMode) {
        this.historyMode = historyMode;
    }

    public Integer getHistoryIntervalMs() {
        return historyIntervalMs;
    }

    public void setHistoryIntervalMs(Integer historyIntervalMs) {
        this.historyIntervalMs = historyIntervalMs;
    }

    public java.math.BigDecimal getChangeThreshold() {
        return changeThreshold;
    }

    public void setChangeThreshold(java.math.BigDecimal changeThreshold) {
        this.changeThreshold = changeThreshold;
    }

    public Boolean getStoreOnChange() {
        return storeOnChange;
    }

    public void setStoreOnChange(Boolean storeOnChange) {
        this.storeOnChange = storeOnChange;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
