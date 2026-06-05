package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.backend.entity.IotAlarmEvent;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.entity.IotAlarmRule;
import com.iot.backend.entity.IotProject;
import com.iot.backend.mapper.IotAlarmEventMapper;
import com.iot.backend.mapper.IotCommDeviceMapper;
import com.iot.backend.mapper.IotCommPointMapper;
import com.iot.backend.mapper.IotProjectMapper;
import com.iot.backend.websocket.WebSocketServer;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AlarmEventService {

    @Autowired
    private IotAlarmEventMapper alarmMapper;

    @Autowired
    private IotProjectMapper projectMapper;

    @Autowired
    private IotCommDeviceMapper deviceMapper;

    @Autowired
    private IotCommPointMapper pointMapper;

    public List<IotAlarmEvent> list(String status, Long projectId, Long deviceId, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<IotAlarmEvent> wrapper = new LambdaQueryWrapper<IotAlarmEvent>()
                .orderByDesc(IotAlarmEvent::getLastTime);
        if (status != null && status.trim().length() > 0) {
            wrapper.eq(IotAlarmEvent::getStatus, status.trim().toUpperCase());
        }
        if (projectId != null) {
            wrapper.eq(IotAlarmEvent::getProjectId, projectId);
        }
        if (deviceId != null) {
            wrapper.eq(IotAlarmEvent::getDeviceId, deviceId);
        }
        if (visibleProjectIds != null) {
            wrapper.in(IotAlarmEvent::getProjectId, visibleProjectIds);
        }
        wrapper.last("LIMIT 300");
        List<IotAlarmEvent> alarms = alarmMapper.selectList(wrapper);
        enrich(alarms);
        return alarms;
    }

    public IotAlarmEvent get(Long id) {
        IotAlarmEvent alarm = alarmMapper.selectById(id);
        if (alarm != null) {
            enrich(Collections.singletonList(alarm));
        }
        return alarm;
    }

    private void enrich(List<IotAlarmEvent> alarms) {
        Map<Long, IotProject> projectCache = new HashMap<>();
        Map<Long, IotCommDevice> deviceCache = new HashMap<>();
        Map<Long, IotCommPoint> pointCache = new HashMap<>();
        for (IotAlarmEvent alarm : alarms) {
            if (alarm.getProjectId() != null) {
                IotProject project = projectCache.computeIfAbsent(alarm.getProjectId(), projectMapper::selectById);
                if (project != null) {
                    alarm.setProjectName(project.getProjectName());
                }
            }
            if (alarm.getDeviceId() != null) {
                IotCommDevice device = deviceCache.computeIfAbsent(alarm.getDeviceId(), deviceMapper::selectById);
                if (device != null) {
                    alarm.setDeviceName(device.getDeviceName());
                }
            }
            if (alarm.getPointId() != null) {
                IotCommPoint point = pointCache.computeIfAbsent(alarm.getPointId(), pointMapper::selectById);
                if (point != null) {
                    alarm.setPointLabel(point.getPointLabel());
                    alarm.setPointKey(point.getPointKey());
                    alarm.setUnit(point.getUnit());
                }
            }
        }
    }

    public void raiseDeviceCollectAlarm(IotCommDevice device, String message) {
        if (device == null || device.getId() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        IotAlarmEvent existing = alarmMapper.selectOne(new LambdaQueryWrapper<IotAlarmEvent>()
                .eq(IotAlarmEvent::getDeviceId, device.getId())
                .eq(IotAlarmEvent::getAlarmType, "COLLECT_ERROR")
                .eq(IotAlarmEvent::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setLastTime(now);
            existing.setMessage(limit(message, 500));
            existing.setOccurCount((existing.getOccurCount() == null ? 1 : existing.getOccurCount()) + 1);
            alarmMapper.updateById(existing);
            notifyAlarmChanged("UPDATE", existing);
            return;
        }

        IotAlarmEvent alarm = new IotAlarmEvent();
        alarm.setProjectId(device.getProjectId());
        alarm.setDeviceId(device.getId());
        alarm.setProtocolType(device.getProtocolType());
        alarm.setAlarmType("COLLECT_ERROR");
        alarm.setSeverity("WARN");
        alarm.setTitle("设备采集异常");
        alarm.setMessage(limit(message, 500));
        alarm.setStatus("ACTIVE");
        alarm.setFirstTime(now);
        alarm.setLastTime(now);
        alarm.setOccurCount(1);
        alarm.setSourceNode(resolveSourceNode());
        alarmMapper.insert(alarm);
        notifyAlarmChanged("RAISE", alarm);
    }

    public void recoverDeviceCollectAlarm(Long deviceId) {
        if (deviceId == null) {
            return;
        }
        IotAlarmEvent existing = alarmMapper.selectOne(new LambdaQueryWrapper<IotAlarmEvent>()
                .eq(IotAlarmEvent::getDeviceId, deviceId)
                .eq(IotAlarmEvent::getAlarmType, "COLLECT_ERROR")
                .eq(IotAlarmEvent::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (existing == null) {
            return;
        }
        long now = System.currentTimeMillis();
        existing.setStatus("RECOVERED");
        existing.setRecoverTime(now);
        existing.setLastTime(now);
        alarmMapper.updateById(existing);
        notifyAlarmChanged("RECOVER", existing);
    }

    public void syncPointCollectAlarms(IotCommDevice device, List<IotCommPoint> points, Map<String, String> pointErrors) {
        if (device == null || device.getId() == null) {
            return;
        }

        Map<String, IotCommPoint> pointIndex = buildPointIndex(points);
        Set<Long> failedPointIds = new HashSet<>();
        if (pointErrors != null && !pointErrors.isEmpty()) {
            for (Map.Entry<String, String> entry : pointErrors.entrySet()) {
                IotCommPoint point = pointIndex.get(normalizeKey(entry.getKey()));
                if (point == null || point.getId() == null) {
                    continue;
                }
                failedPointIds.add(point.getId());
                raisePointCollectAlarm(device, point, entry.getValue());
            }
        }

        List<IotAlarmEvent> activePointAlarms = alarmMapper.selectList(new LambdaQueryWrapper<IotAlarmEvent>()
                .eq(IotAlarmEvent::getDeviceId, device.getId())
                .eq(IotAlarmEvent::getAlarmType, "POINT_COLLECT_ERROR")
                .eq(IotAlarmEvent::getStatus, "ACTIVE"));
        long now = System.currentTimeMillis();
        for (IotAlarmEvent alarm : activePointAlarms) {
            if (alarm.getPointId() == null || !failedPointIds.contains(alarm.getPointId())) {
                alarm.setStatus("RECOVERED");
                alarm.setRecoverTime(now);
                alarm.setLastTime(now);
                alarmMapper.updateById(alarm);
                notifyAlarmChanged("RECOVER", alarm);
            }
        }
    }

    public void raisePointCollectAlarm(IotCommDevice device, IotCommPoint point, String message) {
        if (device == null || device.getId() == null || point == null || point.getId() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        IotAlarmEvent existing = alarmMapper.selectOne(new LambdaQueryWrapper<IotAlarmEvent>()
                .eq(IotAlarmEvent::getDeviceId, device.getId())
                .eq(IotAlarmEvent::getPointId, point.getId())
                .eq(IotAlarmEvent::getAlarmType, "POINT_COLLECT_ERROR")
                .eq(IotAlarmEvent::getStatus, "ACTIVE")
                .last("LIMIT 1"));

        String detail = buildPointCollectMessage(point, message);
        if (existing != null) {
            existing.setLastTime(now);
            existing.setMessage(limit(detail, 500));
            existing.setThresholdText(limit(pointAddressText(point), 200));
            existing.setOccurCount((existing.getOccurCount() == null ? 1 : existing.getOccurCount()) + 1);
            alarmMapper.updateById(existing);
            notifyAlarmChanged("UPDATE", existing);
            return;
        }

        IotAlarmEvent alarm = new IotAlarmEvent();
        alarm.setProjectId(device.getProjectId());
        alarm.setDeviceId(device.getId());
        alarm.setPointId(point.getId());
        alarm.setProtocolType(device.getProtocolType());
        alarm.setAlarmType("POINT_COLLECT_ERROR");
        alarm.setSeverity("WARN");
        alarm.setTitle("点位采集异常");
        alarm.setMessage(limit(detail, 500));
        alarm.setThresholdText(limit(pointAddressText(point), 200));
        alarm.setStatus("ACTIVE");
        alarm.setFirstTime(now);
        alarm.setLastTime(now);
        alarm.setOccurCount(1);
        alarm.setSourceNode(resolveSourceNode());
        alarmMapper.insert(alarm);
        notifyAlarmChanged("RAISE", alarm);
    }

    public boolean resolve(Long id) {
        IotAlarmEvent alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            return false;
        }
        alarm.setStatus("RESOLVED");
        alarm.setRecoverTime(System.currentTimeMillis());
        boolean updated = alarmMapper.updateById(alarm) > 0;
        if (updated) {
            notifyAlarmChanged("RESOLVE", alarm);
        }
        return updated;
    }

    public void linkWorkOrder(Long alarmId, Long workOrderId) {
        if (alarmId == null || workOrderId == null) {
            return;
        }
        IotAlarmEvent alarm = alarmMapper.selectById(alarmId);
        if (alarm == null) {
            return;
        }
        alarm.setWorkOrderId(workOrderId);
        alarmMapper.updateById(alarm);
        notifyAlarmChanged("LINK_WORK_ORDER", alarm);
    }

    public void raiseRuleAlarm(IotCommDevice device, IotAlarmRule rule, String pointLabel, String currentValue, String thresholdText) {
        if (device == null || rule == null || rule.getId() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        IotAlarmEvent existing = alarmMapper.selectOne(new LambdaQueryWrapper<IotAlarmEvent>()
                .eq(IotAlarmEvent::getAlarmRuleId, rule.getId())
                .eq(IotAlarmEvent::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setLastTime(now);
            existing.setCurrentValue(limit(currentValue, 100));
            existing.setThresholdText(limit(thresholdText, 200));
            existing.setOccurCount((existing.getOccurCount() == null ? 1 : existing.getOccurCount()) + 1);
            alarmMapper.updateById(existing);
            notifyAlarmChanged("UPDATE", existing);
            return;
        }

        IotAlarmEvent alarm = new IotAlarmEvent();
        alarm.setProjectId(device.getProjectId());
        alarm.setDeviceId(device.getId());
        alarm.setPointId(rule.getPointId());
        alarm.setAlarmRuleId(rule.getId());
        alarm.setProtocolType(device.getProtocolType());
        alarm.setAlarmType("POINT_RULE");
        alarm.setSeverity(rule.getSeverity());
        alarm.setTitle(limit(rule.getRuleName(), 120));
        alarm.setMessage(limit(pointLabel == null ? rule.getRuleName() : pointLabel + " 触发报警规则", 500));
        alarm.setCurrentValue(limit(currentValue, 100));
        alarm.setThresholdText(limit(thresholdText, 200));
        alarm.setStatus("ACTIVE");
        alarm.setFirstTime(now);
        alarm.setLastTime(now);
        alarm.setOccurCount(1);
        alarm.setSourceNode(resolveSourceNode());
        alarmMapper.insert(alarm);
        notifyAlarmChanged("RAISE", alarm);
    }

    public void recoverRuleAlarm(Long ruleId, String currentValue) {
        if (ruleId == null) {
            return;
        }
        IotAlarmEvent existing = alarmMapper.selectOne(new LambdaQueryWrapper<IotAlarmEvent>()
                .eq(IotAlarmEvent::getAlarmRuleId, ruleId)
                .eq(IotAlarmEvent::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (existing == null) {
            return;
        }
        long now = System.currentTimeMillis();
        existing.setStatus("RECOVERED");
        existing.setRecoverTime(now);
        existing.setLastTime(now);
        existing.setCurrentValue(limit(currentValue, 100));
        alarmMapper.updateById(existing);
        notifyAlarmChanged("RECOVER", existing);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private Map<String, IotCommPoint> buildPointIndex(List<IotCommPoint> points) {
        Map<String, IotCommPoint> index = new HashMap<>();
        if (points == null) {
            return index;
        }
        for (IotCommPoint point : points) {
            if (point == null) {
                continue;
            }
            addPointIndex(index, point.getId() == null ? null : String.valueOf(point.getId()), point);
            addPointIndex(index, point.getPointKey(), point);
            addPointIndex(index, point.getAddress(), point);
            addPointIndex(index, point.getPointLabel(), point);
        }
        return index;
    }

    private void addPointIndex(Map<String, IotCommPoint> index, String key, IotCommPoint point) {
        String normalized = normalizeKey(key);
        if (normalized.length() > 0 && !index.containsKey(normalized)) {
            index.put(normalized, point);
        }
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private String buildPointCollectMessage(IotCommPoint point, String message) {
        String label = firstNonBlank(point.getPointLabel(), point.getPointKey(), point.getAddress(), point.getId() == null ? null : String.valueOf(point.getId()));
        String detail = firstNonBlank(message, "read failed");
        return label + " 采集失败：" + detail;
    }

    private String pointAddressText(IotCommPoint point) {
        if (point == null) {
            return null;
        }
        return "地址 " + firstNonBlank(point.getAddress(), "-")
                + " / FC " + (point.getFunctionCode() == null ? "-" : point.getFunctionCode())
                + " / 从站 " + (point.getSlaveId() == null ? "-" : point.getSlaveId());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private void notifyAlarmChanged(String action, IotAlarmEvent alarm) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "ALARM_CHANGED");
            payload.put("action", action);
            payload.put("alarmId", alarm == null ? null : alarm.getId());
            payload.put("projectId", alarm == null ? null : alarm.getProjectId());
            payload.put("deviceId", alarm == null ? null : alarm.getDeviceId());
            payload.put("status", alarm == null ? null : alarm.getStatus());
            payload.put("time", System.currentTimeMillis());
            WebSocketServer.sendInfo(JSON.toJSONString(payload));
        } catch (Exception ignored) {
            // 报警推送失败不能影响采集和报警状态写库。
        }
    }

    private String resolveSourceNode() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "local";
        }
    }
}
