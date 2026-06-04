package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.entity.IotPointHistory;
import com.iot.backend.mapper.IotPointHistoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PointHistoryService {

    private static final int MAX_QUERY_LIMIT = 5000;
    private static final int DEFAULT_HISTORY_INTERVAL_MS = 300000;
    private static final int VALUE_TEXT_MAX_LENGTH = 100;
    private static final BigDecimal MAX_HISTORY_NUMBER = new BigDecimal("99999999999999.999999");

    @Autowired
    private IotPointHistoryMapper historyMapper;

    private final Map<Long, LastStoredValue> lastStoredByPoint = new ConcurrentHashMap<>();

    public void saveDeviceSnapshot(IotCommDevice device, List<IotCommPoint> points, Map<String, Object> values) {
        if (device == null || points == null || points.isEmpty() || values == null || values.isEmpty()) {
            return;
        }

        long collectTime = parseLong(values.get("timestamp"), System.currentTimeMillis());
        Long collectCostMs = parseNullableLong(values.get("collectCostMs"));
        String collectorNode = limit(valueToText(values.get("collectorNode")), 80);
        String quality = resolveQuality(values.get("status"));

        List<IotPointHistory> rows = new ArrayList<>();
        for (IotCommPoint point : points) {
            if (point == null || point.getPointKey() == null || !values.containsKey(point.getPointKey())) {
                continue;
            }

            Object value = values.get(point.getPointKey());
            FormattedValue formattedValue = formatValue(value, point.getDecimalPlaces());
            if (!shouldStore(device, point, formattedValue, collectTime)) {
                continue;
            }

            IotPointHistory row = new IotPointHistory();
            row.setProjectId(device.getProjectId());
            row.setDeviceId(device.getId());
            row.setPointId(point.getId());
            row.setPointKey(limit(point.getPointKey(), 100));
            row.setPointLabel(limit(point.getPointLabel(), 100));
            row.setProtocolType(limit(device.getProtocolType(), 40));
            row.setValueText(formattedValue.text);
            row.setValueNumber(formattedValue.number);
            row.setRawValue(limit(formattedValue.text, 200));
            row.setQuality(limit(quality, 20));
            row.setCollectTime(collectTime);
            row.setCollectCostMs(collectCostMs);
            row.setCollectorNode(collectorNode);
            rows.add(row);
        }

        for (IotPointHistory row : rows) {
            try {
                historyMapper.insert(row);
                lastStoredByPoint.put(row.getPointId(), new LastStoredValue(collectTime, row.getValueText(), row.getValueNumber()));
            } catch (Exception ex) {
                System.err.println("history row insert failed, deviceId=" + device.getId() + ", pointId=" + row.getPointId() + ", message=" + ex.getMessage());
            }
        }
    }

    public List<IotPointHistory> listHistory(Long pointId, Long deviceId, Long projectId, Long groupId,
                                             Long startTime, Long endTime, Integer limit) {
        QueryWrapper<IotPointHistory> wrapper = new QueryWrapper<>();
        if (pointId != null) {
            wrapper.eq("point_id", pointId);
        }
        if (deviceId != null) {
            wrapper.eq("device_id", deviceId);
        }
        if (projectId != null) {
            wrapper.eq("project_id", projectId);
        }
        if (groupId != null) {
            wrapper.inSql("device_id", "SELECT id FROM iot_comm_device WHERE group_id = " + groupId);
        }
        if (startTime != null) {
            wrapper.ge("collect_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("collect_time", endTime);
        }
        wrapper.orderByDesc("collect_time");
        wrapper.last("LIMIT " + normalizeLimit(limit));
        return historyMapper.selectList(wrapper);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 500;
        }
        return Math.min(limit, MAX_QUERY_LIMIT);
    }

    private String resolveQuality(Object status) {
        String text = valueToText(status);
        if (text == null || text.length() == 0 || "online".equalsIgnoreCase(text) || "online_no_points".equalsIgnoreCase(text)) {
            return "GOOD";
        }
        return "BAD";
    }

    private String valueToText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return limit(text, VALUE_TEXT_MAX_LENGTH);
    }

    private FormattedValue formatValue(Object value, Integer decimalPlaces) {
        if (!(value instanceof Number)) {
            return new FormattedValue(valueToText(value), null);
        }
        double rawNumber = ((Number) value).doubleValue();
        if (Double.isNaN(rawNumber) || Double.isInfinite(rawNumber)) {
            return new FormattedValue(valueToText(value), null);
        }

        int scale = decimalPlaces == null ? 2 : decimalPlaces;
        if (scale < 0) {
            scale = 0;
        }
        if (scale > 6) {
            scale = 6;
        }

        BigDecimal number = BigDecimal.valueOf(rawNumber).setScale(scale, RoundingMode.HALF_UP);
        String text = valueToText(number.toPlainString());
        if (number.abs().compareTo(MAX_HISTORY_NUMBER) > 0) {
            return new FormattedValue(text, null);
        }
        return new FormattedValue(text, number);
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private boolean shouldStore(IotCommDevice device, IotCommPoint point, FormattedValue value, long collectTime) {
        if (point.getId() == null) {
            return false;
        }

        HistoryStrategy strategy = resolveStrategy(device, point);
        if (!strategy.enabled) {
            return false;
        }

        String mode = strategy.mode;
        if ("DISABLED".equals(mode)) {
            return false;
        }

        LastStoredValue last = lastStoredByPoint.get(point.getId());
        if (last == null) {
            return true;
        }

        boolean intervalReached = collectTime - last.collectTime >= strategy.intervalMs;
        boolean changed = hasChanged(strategy, value, last);

        if ("INTERVAL".equals(mode)) {
            return intervalReached;
        }
        if ("CHANGE".equals(mode)) {
            return changed;
        }
        return intervalReached || changed;
    }

    private HistoryStrategy resolveStrategy(IotCommDevice device, IotCommPoint point) {
        String pointMode = point.getHistoryMode() == null ? "INHERIT" : point.getHistoryMode().trim().toUpperCase();
        boolean inherit = "INHERIT".equals(pointMode) || pointMode.length() == 0;

        HistoryStrategy strategy = new HistoryStrategy();
        if (inherit) {
            strategy.enabled = !Boolean.FALSE.equals(device.getHistoryEnabled());
            strategy.mode = device.getHistoryMode() == null ? "INTERVAL_CHANGE" : device.getHistoryMode().trim().toUpperCase();
            strategy.intervalMs = normalizeInterval(device.getHistoryIntervalMs());
            strategy.changeThreshold = device.getChangeThreshold();
            strategy.storeOnChange = !Boolean.FALSE.equals(device.getStoreOnChange());
            return strategy;
        }

        strategy.enabled = !Boolean.FALSE.equals(point.getHistoryEnabled());
        strategy.mode = pointMode;
        strategy.intervalMs = normalizeInterval(point.getHistoryIntervalMs());
        strategy.changeThreshold = point.getChangeThreshold();
        strategy.storeOnChange = !Boolean.FALSE.equals(point.getStoreOnChange());
        return strategy;
    }

    private long normalizeInterval(Integer intervalMs) {
        if (intervalMs == null || intervalMs < 1000) {
            return DEFAULT_HISTORY_INTERVAL_MS;
        }
        return intervalMs;
    }

    private boolean hasChanged(HistoryStrategy strategy, FormattedValue value, LastStoredValue last) {
        if (!strategy.storeOnChange) {
            return false;
        }
        if (value.number != null && last.number != null) {
            BigDecimal threshold = strategy.changeThreshold;
            if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
                return !value.number.equals(last.number);
            }
            return value.number.subtract(last.number).abs().compareTo(threshold) >= 0;
        }
        return value.text != null && !value.text.equals(last.text);
    }

    private Long parseNullableLong(Object value) {
        if (value == null) {
            return null;
        }
        return parseLong(value, null);
    }

    private Long parseLong(Object value, Long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static class FormattedValue {
        private final String text;
        private final BigDecimal number;

        private FormattedValue(String text, BigDecimal number) {
            this.text = text;
            this.number = number;
        }
    }

    private static class LastStoredValue {
        private final long collectTime;
        private final String text;
        private final BigDecimal number;

        private LastStoredValue(long collectTime, String text, BigDecimal number) {
            this.collectTime = collectTime;
            this.text = text;
            this.number = number;
        }
    }

    private static class HistoryStrategy {
        private boolean enabled;
        private String mode;
        private long intervalMs;
        private BigDecimal changeThreshold;
        private boolean storeOnChange;
    }
}
