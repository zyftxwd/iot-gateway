package com.iot.backend.dto;

import com.iot.backend.entity.IotCommPoint;

public class PointRuntimeValue {
    private IotCommPoint point;
    private Object value;
    private Long updateTime;
    private Boolean writable;

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
}
