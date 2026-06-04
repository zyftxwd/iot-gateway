package com.iot.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.backend.common.Result;
import com.iot.backend.entity.IotAlarmEvent;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotPointHistory;
import com.iot.backend.entity.IotWorkOrder;
import com.iot.backend.mapper.IotAlarmEventMapper;
import com.iot.backend.mapper.IotCommDeviceMapper;
import com.iot.backend.mapper.IotPointHistoryMapper;
import com.iot.backend.mapper.IotWorkOrderMapper;
import com.iot.backend.service.PermissionService;
import com.iot.backend.task.GatewayEngineTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    @Autowired
    private IotCommDeviceMapper deviceMapper;

    @Autowired
    private IotPointHistoryMapper historyMapper;

    @Autowired
    private IotAlarmEventMapper alarmMapper;

    @Autowired
    private IotWorkOrderMapper workOrderMapper;

    @Autowired
    private GatewayEngineTask gatewayEngineTask;

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestHeader(value = "Authorization", required = false) String authorization) {
        List<Long> visibleProjectIds = permissionService.visibleProjectIds(authorization);
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("summary", summary(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
            empty.put("collector", gatewayEngineTask.getCollectorStatus());
            empty.put("protocols", new ArrayList<>());
            empty.put("deviceErrors", new ArrayList<>());
            empty.put("badHistory", new ArrayList<>());
            empty.put("activeAlarms", new ArrayList<>());
            empty.put("openWorkOrders", new ArrayList<>());
            empty.put("generatedAt", System.currentTimeMillis());
            return Result.success(empty);
        }
        List<IotCommDevice> devices = deviceMapper.selectList(deviceScope(new LambdaQueryWrapper<>(), visibleProjectIds));
        List<IotAlarmEvent> activeAlarms = alarmMapper.selectList(alarmScope(new LambdaQueryWrapper<IotAlarmEvent>()
                .eq(IotAlarmEvent::getStatus, "ACTIVE"), visibleProjectIds));
        List<IotWorkOrder> openOrders = workOrderMapper.selectList(orderScope(new LambdaQueryWrapper<IotWorkOrder>()
                .ne(IotWorkOrder::getStatus, "CLOSED"), visibleProjectIds));
        List<IotPointHistory> recentBadHistory = historyMapper.selectList(historyScope(new LambdaQueryWrapper<IotPointHistory>()
                .ne(IotPointHistory::getQuality, "GOOD")
                .orderByDesc(IotPointHistory::getCollectTime)
                .last("LIMIT 50"), visibleProjectIds));

        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary(devices, activeAlarms, openOrders, recentBadHistory));
        result.put("collector", gatewayEngineTask.getCollectorStatus());
        result.put("protocols", protocols(devices));
        result.put("deviceErrors", deviceErrors(devices));
        result.put("badHistory", recentBadHistory);
        result.put("activeAlarms", activeAlarms);
        result.put("openWorkOrders", openOrders);
        result.put("generatedAt", System.currentTimeMillis());
        return Result.success(result);
    }

    private Map<String, Object> summary(List<IotCommDevice> devices,
                                        List<IotAlarmEvent> activeAlarms,
                                        List<IotWorkOrder> openOrders,
                                        List<IotPointHistory> recentBadHistory) {
        int online = 0;
        int offline = 0;
        int unknown = 0;
        long lastCollectTime = 0L;
        long lastSuccessTime = 0L;
        for (IotCommDevice device : devices) {
            String status = device.getStatus() == null ? "UNKNOWN" : device.getStatus();
            if ("ONLINE".equalsIgnoreCase(status)) {
                online++;
            } else if ("OFFLINE".equalsIgnoreCase(status)) {
                offline++;
            } else {
                unknown++;
            }
            if (device.getLastCollectTime() != null && device.getLastCollectTime() > lastCollectTime) {
                lastCollectTime = device.getLastCollectTime();
            }
            if (device.getLastSuccessTime() != null && device.getLastSuccessTime() > lastSuccessTime) {
                lastSuccessTime = device.getLastSuccessTime();
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("deviceTotal", devices.size());
        summary.put("onlineDevices", online);
        summary.put("offlineDevices", offline);
        summary.put("unknownDevices", unknown);
        summary.put("activeAlarms", activeAlarms.size());
        summary.put("openWorkOrders", openOrders.size());
        summary.put("recentBadSamples", recentBadHistory.size());
        summary.put("lastCollectTime", lastCollectTime == 0L ? null : lastCollectTime);
        summary.put("lastSuccessTime", lastSuccessTime == 0L ? null : lastSuccessTime);
        return summary;
    }

    private List<Map<String, Object>> protocols(List<IotCommDevice> devices) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (IotCommDevice device : devices) {
            String protocol = device.getProtocolType() == null ? "UNKNOWN" : device.getProtocolType();
            Map<String, Object> item = grouped.computeIfAbsent(protocol, key -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("protocolType", key);
                row.put("total", 0);
                row.put("online", 0);
                row.put("offline", 0);
                row.put("unknown", 0);
                row.put("lastCollectTime", null);
                return row;
            });
            item.put("total", ((Integer) item.get("total")) + 1);
            String status = device.getStatus() == null ? "UNKNOWN" : device.getStatus();
            if ("ONLINE".equalsIgnoreCase(status)) {
                item.put("online", ((Integer) item.get("online")) + 1);
            } else if ("OFFLINE".equalsIgnoreCase(status)) {
                item.put("offline", ((Integer) item.get("offline")) + 1);
            } else {
                item.put("unknown", ((Integer) item.get("unknown")) + 1);
            }
            Long lastCollectTime = (Long) item.get("lastCollectTime");
            if (device.getLastCollectTime() != null && (lastCollectTime == null || device.getLastCollectTime() > lastCollectTime)) {
                item.put("lastCollectTime", device.getLastCollectTime());
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private List<Map<String, Object>> deviceErrors(List<IotCommDevice> devices) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IotCommDevice device : devices) {
            if (!"OFFLINE".equalsIgnoreCase(device.getStatus()) && (device.getLastErrorMessage() == null || device.getLastErrorMessage().trim().length() == 0)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", device.getId());
            row.put("deviceName", device.getDeviceName());
            row.put("protocolType", device.getProtocolType());
            row.put("address", device.getIpAddress() + ":" + device.getPort());
            row.put("status", device.getStatus());
            row.put("failCount", device.getFailCount());
            row.put("lastCollectTime", device.getLastCollectTime());
            row.put("lastErrorMessage", device.getLastErrorMessage());
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(asLong(b.get("lastCollectTime")), asLong(a.get("lastCollectTime"))));
        return rows.size() > 50 ? rows.subList(0, 50) : rows;
    }

    private long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private LambdaQueryWrapper<IotCommDevice> deviceScope(LambdaQueryWrapper<IotCommDevice> wrapper, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null) {
            wrapper.in(IotCommDevice::getProjectId, visibleProjectIds);
        }
        return wrapper.orderByDesc(IotCommDevice::getLastCollectTime);
    }

    private LambdaQueryWrapper<IotAlarmEvent> alarmScope(LambdaQueryWrapper<IotAlarmEvent> wrapper, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null) {
            wrapper.in(IotAlarmEvent::getProjectId, visibleProjectIds);
        }
        return wrapper.orderByDesc(IotAlarmEvent::getLastTime).last("LIMIT 50");
    }

    private LambdaQueryWrapper<IotWorkOrder> orderScope(LambdaQueryWrapper<IotWorkOrder> wrapper, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null) {
            wrapper.in(IotWorkOrder::getProjectId, visibleProjectIds);
        }
        return wrapper.orderByDesc(IotWorkOrder::getCreateTime).last("LIMIT 50");
    }

    private LambdaQueryWrapper<IotPointHistory> historyScope(LambdaQueryWrapper<IotPointHistory> wrapper, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null) {
            wrapper.in(IotPointHistory::getProjectId, visibleProjectIds);
        }
        return wrapper;
    }
}
