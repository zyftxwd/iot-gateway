package com.iot.backend.task;

import com.alibaba.fastjson2.JSON;
import com.iot.backend.dto.DeviceFullConfig;
import com.iot.backend.dto.DeviceCollectSnapshot;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.protocol.IProtocolHandler;
import com.iot.backend.protocol.ProtocolHandlerFactory;
import com.iot.backend.service.AlarmEventService;
import com.iot.backend.service.IotCommDeviceService;
import com.iot.backend.service.PointRuntimeDataService;
import com.iot.backend.service.PointValuePipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gateway collection scheduler.
 *
 * The scheduler only dispatches due devices. Real protocol IO runs in a bounded
 * worker pool so slow or offline devices do not block the whole gateway.
 */
@Component
public class GatewayEngineTask {

    @Autowired
    private IotCommDeviceService deviceService;

    @Autowired
    private ProtocolHandlerFactory protocolFactory;

    @Autowired
    private PointValuePipeline pointValuePipeline;

    @Autowired
    private AlarmEventService alarmEventService;

    @Autowired
    private PointRuntimeDataService runtimeDataService;

    @Value("${industrial.collector.pool-size:8}")
    private int poolSize;

    @Value("${industrial.collector.queue-capacity:2000}")
    private int queueCapacity;

    @Value("${industrial.collector.node-id:}")
    private String configuredNodeId;

    private final Set<Long> runningDeviceIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger threadIndex = new AtomicInteger(1);
    private ThreadPoolExecutor collectorExecutor;
    private String collectorNodeId;

    @PostConstruct
    public void initCollector() {
        int workers = Math.max(1, poolSize);
        int queueSize = Math.max(100, queueCapacity);
        collectorNodeId = resolveCollectorNodeId();
        collectorExecutor = new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueSize),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("collector-" + collectorNodeId + "-" + threadIndex.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        System.out.println("collector started, node=" + collectorNodeId + ", workers=" + workers + ", queue=" + queueSize);
    }

    @PreDestroy
    public void shutdownCollector() {
        if (collectorExecutor != null) {
            collectorExecutor.shutdownNow();
        }
    }

    @Scheduled(fixedRateString = "${industrial.collector.dispatch-rate-ms:1000}")
    public void pollDevices() {
        List<IotCommDevice> devices = deviceService.listDevices(null, null, null, null);
        if (devices == null || devices.isEmpty()) {
            return;
        }

        for (IotCommDevice device : devices) {
            if (!shouldCollect(device) || !runningDeviceIds.add(device.getId())) {
                continue;
            }

            try {
                collectorExecutor.execute(() -> {
                    try {
                        collectOneDevice(device);
                    } finally {
                        runningDeviceIds.remove(device.getId());
                    }
                });
            } catch (Exception e) {
                runningDeviceIds.remove(device.getId());
                safeMarkFailure(device, "采集任务队列已满或线程池不可用：" + e.getMessage());
            }
        }
    }

    private void collectOneDevice(IotCommDevice device) {
        long startTime = System.currentTimeMillis();
        IProtocolHandler handler = null;
        try {
            DeviceFullConfig config = deviceService.getFullConfig(device.getId());
            if (config == null || config.getDevice() == null) {
                return;
            }

            handler = protocolFactory.getHandler(device.getProtocolType());
            Map<String, Object> extParams = parseExtParams(device.getExtConfig());
            extParams.put("_deviceId", device.getId());
            int port = resolvePort(device);

            if (handler.connect(device.getIpAddress(), port, extParams)) {
                Map<String, Object> deviceData = handler.readData(config.getPoints());
                Map<String, String> pointErrors = extractPointErrors(deviceData.get("_pointErrors"));
                String collectStatus = String.valueOf(deviceData.getOrDefault("_collectStatus", deviceData.get("status")));
                long costMs = System.currentTimeMillis() - startTime;

                if ("error".equalsIgnoreCase(collectStatus)) {
                    safeMarkFailure(device, String.valueOf(deviceData.getOrDefault("_errorMessage", "协议读取失败")));
                } else {
                    try {
                        deviceService.markCollectSuccess(device.getId());
                    } catch (Exception statusError) {
                        System.err.println("mark collect success failed, deviceId=" + device.getId() + ", message=" + statusError.getMessage());
                    }
                    try {
                        alarmEventService.recoverDeviceCollectAlarm(device.getId());
                    } catch (Exception alarmError) {
                        System.err.println("recover collect alarm failed, deviceId=" + device.getId() + ", message=" + alarmError.getMessage());
                    }
                    try {
                        alarmEventService.syncPointCollectAlarms(device, config.getPoints(), pointErrors);
                    } catch (Exception pointAlarmError) {
                        System.err.println("sync point collect alarms failed, deviceId=" + device.getId() + ", message=" + pointAlarmError.getMessage());
                    }
                    try {
                        runtimeDataService.clearPointValues(device.getId(), failedPointKeys(config.getPoints(), pointErrors));
                    } catch (Exception runtimeError) {
                        System.err.println("clear failed point runtime values failed, deviceId=" + device.getId() + ", message=" + runtimeError.getMessage());
                    }
                }

                enrichDeviceData(device, deviceData, costMs);
                handleCollectValues(device, config.getPoints(), deviceData, !"error".equalsIgnoreCase(collectStatus));
            } else {
                safeMarkFailure(device, "连接失败：" + device.getIpAddress() + ":" + port);
            }
        } catch (Exception e) {
            safeMarkFailure(device, e.getMessage());
            System.err.println("collect failed, deviceId=" + device.getId() + ", message=" + e.getMessage());
        } finally {
            if (handler != null) {
                try {
                    handler.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean shouldCollect(IotCommDevice device) {
        int interval = device.getCollectIntervalMs() == null || device.getCollectIntervalMs() < 1000
                ? 1000
                : device.getCollectIntervalMs();
        Long lastCollectTime = device.getLastCollectTime();
        return lastCollectTime == null || System.currentTimeMillis() - lastCollectTime >= interval;
    }

    private void markFailure(IotCommDevice device, String errorMessage) {
        String message = errorMessage == null || errorMessage.trim().length() == 0 ? "采集失败" : errorMessage;
        deviceService.markCollectFailure(device.getId(), message);
        runtimeDataService.clearDeviceValues(device.getId());
        alarmEventService.raiseDeviceCollectAlarm(device, message);
    }

    private void safeMarkFailure(IotCommDevice device, String errorMessage) {
        if (device == null || device.getId() == null) {
            return;
        }
        try {
            markFailure(device, errorMessage);
        } catch (Exception failureError) {
            System.err.println("mark collect failure failed, deviceId=" + device.getId() + ", message=" + failureError.getMessage());
        }
    }

    private void enrichDeviceData(IotCommDevice device, Map<String, Object> deviceData, long costMs) {
        deviceData.put("deviceId", device.getId());
        deviceData.put("deviceName", device.getDeviceName());
        deviceData.put("protocolType", device.getProtocolType());
        deviceData.put("timestamp", System.currentTimeMillis());
        deviceData.put("collectCostMs", costMs);
        deviceData.put("collectorNode", collectorNodeId);
    }

    private Map<String, Object> parseExtParams(String extConfig) {
        if (extConfig == null || extConfig.trim().length() == 0) {
            return new HashMap<>();
        }

        try {
            return JSON.parseObject(extConfig, Map.class);
        } catch (Exception e) {
            System.err.println("invalid extConfig, fallback to empty params: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, String> extractPointErrors(Object rawErrors) {
        Map<String, String> errors = new HashMap<>();
        if (!(rawErrors instanceof Map)) {
            return errors;
        }

        Map<?, ?> rawMap = (Map<?, ?>) rawErrors;
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            errors.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return errors;
    }

    private Set<String> failedPointKeys(List<IotCommPoint> points, Map<String, String> pointErrors) {
        Set<String> result = new HashSet<>();
        if (points == null || pointErrors == null || pointErrors.isEmpty()) {
            return result;
        }

        Map<String, IotCommPoint> pointIndex = new HashMap<>();
        for (IotCommPoint point : points) {
            if (point == null) {
                continue;
            }
            addPointIndex(pointIndex, point.getId() == null ? null : String.valueOf(point.getId()), point);
            addPointIndex(pointIndex, point.getPointKey(), point);
            addPointIndex(pointIndex, point.getAddress(), point);
            addPointIndex(pointIndex, point.getPointLabel(), point);
        }

        for (String errorKey : pointErrors.keySet()) {
            IotCommPoint point = pointIndex.get(normalizePointKey(errorKey));
            if (point != null && point.getPointKey() != null) {
                result.add(point.getPointKey());
            }
        }
        return result;
    }

    private void addPointIndex(Map<String, IotCommPoint> pointIndex, String key, IotCommPoint point) {
        String normalized = normalizePointKey(key);
        if (normalized.length() > 0 && !pointIndex.containsKey(normalized)) {
            pointIndex.put(normalized, point);
        }
    }

    private String normalizePointKey(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private int resolvePort(IotCommDevice device) {
        if (device.getPort() != null && device.getPort() > 0) {
            return device.getPort();
        }
        return protocolFactory.defaultPort(device.getProtocolType());
    }

    private void handleCollectValues(IotCommDevice device, List<IotCommPoint> points, Map<String, Object> values, boolean storeHistory) {
        DeviceCollectSnapshot snapshot = new DeviceCollectSnapshot();
        snapshot.setDevice(device);
        snapshot.setPoints(points);
        snapshot.setValues(values);
        snapshot.setStoreHistory(storeHistory);
        pointValuePipeline.handle(snapshot);
    }

    private String resolveCollectorNodeId() {
        if (configuredNodeId != null && configuredNodeId.trim().length() > 0) {
            return configuredNodeId.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "local-node";
        }
    }

    public Map<String, Object> getCollectorStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("nodeId", collectorNodeId);
        status.put("poolSize", collectorExecutor == null ? 0 : collectorExecutor.getPoolSize());
        status.put("corePoolSize", collectorExecutor == null ? 0 : collectorExecutor.getCorePoolSize());
        status.put("activeThreads", collectorExecutor == null ? 0 : collectorExecutor.getActiveCount());
        status.put("queuedTasks", collectorExecutor == null ? 0 : collectorExecutor.getQueue().size());
        status.put("queueCapacity", queueCapacity);
        status.put("runningDevices", runningDeviceIds.size());
        status.put("completedTasks", collectorExecutor == null ? 0 : collectorExecutor.getCompletedTaskCount());
        return status;
    }
}
