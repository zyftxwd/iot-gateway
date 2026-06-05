package com.iot.backend.dto;

import com.iot.backend.entity.IotCommPoint;

public class PointRuntimeValue {
    private IotCommPoint point;
    private Object value;
    private Long updateTime;
    private Boolean writable;
    private String collectStatus;
    private String collectErrorMessage;
    private Long collectErrorTime;

    public IotCommPoint getPoint() {
        return point;
    }

    public void setPoint(IotCommPoint point) {
        this.point = point;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean getWritable() {
        return writable;
    }

    public void setWritable(Boolean writable) {
        this.writable = writable;
    }

    public String getCollectStatus() {
        return collectStatus;
    }

    public void setCollectStatus(String collectStatus) {
        this.collectStatus = collectStatus;
    }

    public String getCollectErrorMessage() {
        return collectErrorMessage;
    }

    public void setCollectErrorMessage(String collectErrorMessage) {
        this.collectErrorMessage = collectErrorMessage;
    }

    public Long getCollectErrorTime() {
        return collectErrorTime;
    }

    public void setCollectErrorTime(Long collectErrorTime) {
        this.collectErrorTime = collectErrorTime;
    }
}
