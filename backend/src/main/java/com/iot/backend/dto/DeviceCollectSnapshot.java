package com.iot.backend.dto;

import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;

import java.util.List;
import java.util.Map;

public class DeviceCollectSnapshot {
    private IotCommDevice device;
    private List<IotCommPoint> points;
    private Map<String, Object> values;
    private boolean storeHistory;

    public IotCommDevice getDevice() {
        return device;
    }

    public void setDevice(IotCommDevice device) {
        this.device = device;
    }

    public List<IotCommPoint> getPoints() {
        return points;
    }

    public void setPoints(List<IotCommPoint> points) {
        this.points = points;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    public boolean isStoreHistory() {
        return storeHistory;
    }

    public void setStoreHistory(boolean storeHistory) {
        this.storeHistory = storeHistory;
    }
}
