package com.iot.backend.service;

import com.iot.backend.dto.DeviceCollectSnapshot;
import com.iot.backend.entity.IotAlarmRule;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlarmEvaluateService {

    @Autowired
    private AlarmRuleService ruleService;

    @Autowired
    private AlarmEventService alarmEventService;

    private final Map<Long, RuleRuntimeState> states = new ConcurrentHashMap<>();

    public void evaluate(DeviceCollectSnapshot snapshot) {
        if (snapshot == null || snapshot.getDevice() == null || snapshot.getValues() == null) {
            return;
        }

        IotCommDevice device = snapshot.getDevice();
        List<IotAlarmRule> rules = ruleService.listEnabledByDevice(device.getId());
        if (rules == null || rules.isEmpty()) {
            return;
        }

        Map<Long, IotCommPoint> pointMap = new HashMap<>();
        if (snapshot.getPoints() != null) {
            for (IotCommPoint point : snapshot.getPoints()) {
                pointMap.put(point.getId(), point);
            }
        }

        long now = System.currentTimeMillis();
        for (IotAlarmRule rule : rules) {
            IotCommPoint point = pointMap.get(rule.getPointId());
            if (point == null || point.getPointKey() == null || !snapshot.getValues().containsKey(point.getPointKey())) {
                continue;
            }
            BigDecimal value = toDecimal(snapshot.getValues().get(point.getPointKey()));
            if (value == null) {
                continue;
            }
            applyRule(device, point, rule, value, now);
        }
    }

    private void applyRule(IotCommDevice device, IotCommPoint point, IotAlarmRule rule, BigDecimal value, long now) {
        RuleRuntimeState state = states.computeIfAbsent(rule.getId(), key -> new RuleRuntimeState());
        boolean alarmMatched = matchAlarm(rule, value);
        String currentValue = formatDecimal(value, point.getDecimalPlaces());

        if (alarmMatched) {
            state.recoverStartTime = null;
            if (state.triggerStartTime == null) {
                state.triggerStartTime = now;
            }
            long triggerDuration = Boolean.TRUE.equals(rule.getImmediateAlarm()) ? 0 : safeMs(rule.getTriggerDurationMs(), 60000);
            if (now - state.triggerStartTime >= triggerDuration) {
                state.active = true;
                alarmEventService.raiseRuleAlarm(device, rule, point.getPointLabel(), currentValue, thresholdText(rule, point));
            }
            return;
        }

        state.triggerStartTime = null;
        if (state.recoverStartTime == null) {
            state.recoverStartTime = now;
        }
        long recoverDuration = safeMs(rule.getRecoverDurationMs(), 300000);
        if (now - state.recoverStartTime >= recoverDuration) {
            state.active = false;
            state.recoverStartTime = null;
            alarmEventService.recoverRuleAlarm(rule.getId(), currentValue);
        }
    }

    private boolean matchAlarm(IotAlarmRule rule, BigDecimal value) {
        String type = normalize(rule.getConditionType());
        BigDecimal threshold = rule.getThresholdValue();
        BigDecimal high = rule.getThresholdHigh();
        if ("GT".equals(type)) return threshold != null && value.compareTo(threshold) > 0;
        if ("GTE".equals(type)) return threshold != null && value.compareTo(threshold) >= 0;
        if ("LT".equals(type)) return threshold != null && value.compareTo(threshold) < 0;
        if ("LTE".equals(type)) return threshold != null && value.compareTo(threshold) <= 0;
        if ("EQ".equals(type)) return threshold != null && value.compareTo(threshold) == 0;
        if ("NE".equals(type)) return threshold != null && value.compareTo(threshold) != 0;
        if ("OUT_RANGE".equals(type)) return threshold != null && high != null && (value.compareTo(threshold) < 0 || value.compareTo(high) > 0);
        if ("IN_RANGE".equals(type)) return threshold != null && high != null && value.compareTo(threshold) >= 0 && value.compareTo(high) <= 0;
        return false;
    }

    private String thresholdText(IotAlarmRule rule, IotCommPoint point) {
        String type = normalize(rule.getConditionType());
        if ("OUT_RANGE".equals(type) || "IN_RANGE".equals(type)) {
            return type + " [" + formatDecimal(rule.getThresholdValue(), point.getDecimalPlaces()) + ", "
                    + formatDecimal(rule.getThresholdHigh(), point.getDecimalPlaces()) + "]";
        }
        return type + " " + formatDecimal(rule.getThresholdValue(), point.getDecimalPlaces());
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private long safeMs(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    private String formatDecimal(BigDecimal value, Integer decimalPlaces) {
        if (value == null) {
            return "-";
        }
        int scale = decimalPlaces == null ? 2 : Math.max(0, Math.min(decimalPlaces, 6));
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static class RuleRuntimeState {
        private Long triggerStartTime;
        private Long recoverStartTime;
        private boolean active;
    }
}
