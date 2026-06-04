package com.iot.backend.service;

import com.iot.backend.dto.PointRuntimeValue;
import com.iot.backend.entity.IotCommPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PointRuntimeDataService {

    private final Map<Long, Map<String, RuntimeValue>> latestByDevice = new ConcurrentHashMap<>();

    public void updateDeviceValues(Long deviceId, Map<String, Object> values) {
        if (deviceId == null || values == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, RuntimeValue> deviceValues = latestByDevice.computeIfAbsent(deviceId, key -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                deviceValues.put(entry.getKey(), new RuntimeValue(entry.getValue(), now));
            }
        }
    }

    public void updatePointValue(Long deviceId, String pointKey, Object value) {
        if (deviceId == null || pointKey == null) {
            return;
        }
        latestByDevice.computeIfAbsent(deviceId, key -> new ConcurrentHashMap<>())
                .put(pointKey, new RuntimeValue(value, System.currentTimeMillis()));
    }

    public void clearDeviceValues(Long deviceId) {
        if (deviceId != null) {
            latestByDevice.remove(deviceId);
        }
    }

    public List<PointRuntimeValue> buildRuntimeValues(Long deviceId, List<IotCommPoint> points) {
        Map<String, RuntimeValue> deviceValues = latestByDevice.get(deviceId);
        List<PointRuntimeValue> result = new ArrayList<>();

        if (points == null) {
            return result;
        }

        for (IotCommPoint point : points) {
            RuntimeValue runtimeValue = deviceValues == null ? null : deviceValues.get(point.getPointKey());
            PointRuntimeValue item = new PointRuntimeValue();
            item.setPoint(point);
            item.setValue(runtimeValue == null ? null : formatValue(runtimeValue.value, point.getDecimalPlaces()));
            item.setUpdateTime(runtimeValue == null ? null : runtimeValue.updateTime);
            item.setWritable("READ_WRITE".equalsIgnoreCase(point.getAccessMode()));
            result.add(item);
        }
        return result;
    }

    private Object formatValue(Object value, Integer decimalPlaces) {
        if (!(value instanceof Number)) {
            return value;
        }
        int scale = decimalPlaces == null ? 2 : decimalPlaces;
        if (scale < 0) {
            scale = 0;
        }
        if (scale > 6) {
            scale = 6;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue())
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static class RuntimeValue {
        private final Object value;
        private final Long updateTime;

        private RuntimeValue(Object value, Long updateTime) {
            this.value = value;
            this.updateTime = updateTime;
        }
    }
}
