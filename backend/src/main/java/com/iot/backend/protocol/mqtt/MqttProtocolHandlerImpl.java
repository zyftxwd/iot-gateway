package com.iot.backend.protocol.mqtt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.protocol.IProtocolHandler;
import com.iot.backend.protocol.ProtocolField;
import com.iot.backend.protocol.ProtocolMetadata;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业协议：MQTT 核心实现 (被动接收，主动缓存)
 */

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MqttProtocolHandlerImpl implements IProtocolHandler {

    private MqttClient mqttClient;
    private String subTopic = "#"; // 默认订阅所有主题
    private String publishTopic;
    private String ackTopic;
    private String writePayloadTemplate;
    private String ackMatchMode = "REQUEST_ID_OR_VALUE";
    private String ackRequestIdPath = "requestId";
    private String ackSuccessPath = "success";
    private String ackStatusPath = "status";
    private String ackCodePath = "code";
    private String ackPointKeyPath = "pointKey";
    private String ackAddressPath = "address";
    private String ackValuePath = "value";
    private String ackSuccessValues = "true,success,ok,done,0,200";
    private String ackFailureValues = "false,fail,failed,error,-1,500";
    private int qos = 0;
    private boolean cleanSession = true;
    private int writeConfirmTimeoutMs = 3000;
    private int staleTimeoutMs = 5000;

    private static final Map<String, MqttClient> CLIENTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> CACHES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Map<String, Object>>> ACK_CACHES = new ConcurrentHashMap<>();

    // MQTT 是设备主动推数据的，缓存必须跨采集轮次保留。
    private Map<String, Object> latestDataCache = new ConcurrentHashMap<>();
    private Map<String, Map<String, Object>> writeAckCache = new ConcurrentHashMap<>();
    private Map<String, Object> configuredExtParams = new HashMap<>();

    public static void closeDeviceConnections(Long deviceId) {
        if (deviceId == null) {
            return;
        }
        String prefix = String.valueOf(deviceId) + "|";
        CLIENTS.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) {
                return false;
            }
            try {
                MqttClient client = entry.getValue();
                if (client != null && client.isConnected()) {
                    client.disconnectForcibly(500, 500);
                }
                if (client != null) {
                    client.close(true);
                }
            } catch (Exception ignored) {
            }
            return true;
        });
        CACHES.keySet().removeIf(key -> key.startsWith(prefix));
        ACK_CACHES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public String getProtocolType() {
        return "MQTT";
    }

    @Override
    public ProtocolMetadata metadata() {
        return new ProtocolMetadata("MQTT", "MQTT", "MQTT Broker 订阅采集，适合设备主动上报 JSON 数据。", true)
                .capabilities(true, true, false, true, false)
                .addField(ProtocolField.device("ipAddress", "Broker地址", "text", true, "127.0.0.1", "例如：192.168.1.20"))
                .addField(ProtocolField.device("port", "端口", "number", true, 1883, "默认 1883"))
                .addField(ProtocolField.ext("topic", "订阅主题", "text", true, "#", "例如：factory/+/data"))
                .addField(ProtocolField.ext("publishTopic", "写入主题", "text", false, "", "允许写入时发布到该主题"))
                .addField(ProtocolField.ext("writablePointKeys", "可写字段路径", "text", false, "setpoint,fan_start", "逗号分隔，自动发现点位时这些字段默认标记为读写"))
                .addField(ProtocolField.ext("ackTopic", "写入确认主题", "text", false, "", "设备返回写入结果的主题，例如：factory/device/ack").advanced().group("写入确认"))
                .addField(ProtocolField.ext("writePayloadTemplate", "写入报文模板", "textarea", false, "", "可选 JSON 模板，支持 ${requestId}、${pointKey}、${address}、${valueJson}、${deviceCode}").advanced().group("写入确认"))
                .addField(ProtocolField.ext("deviceCode", "设备编码", "text", false, "", "厂家网关或 EMQX 侧使用的设备编码，可用于主题和报文模板").advanced().group("业务标识"))
                .addField(ProtocolField.ext("projectCode", "项目编码", "text", false, "", "厂家网关或平台侧项目 ID，可用于主题和报文模板").advanced().group("业务标识"))
                .addField(ProtocolField.ext("ackMatchMode", "确认匹配方式", "text", false, "REQUEST_ID_OR_VALUE", "REQUEST_ID、REQUEST_ID_OR_VALUE、POINT_VALUE、ANY_SUCCESS").advanced().group("写入确认"))
                .addField(ProtocolField.ext("ackRequestIdPath", "确认ID字段", "text", false, "requestId", "ACK 中请求 ID 字段路径，例如 data.requestId").advanced().group("写入确认"))
                .addField(ProtocolField.ext("ackSuccessPath", "成功字段", "text", false, "success", "ACK 中成功标识字段，例如 success 或 data.ok").advanced().group("写入确认"))
                .addField(ProtocolField.ext("ackStatusPath", "状态字段", "text", false, "status", "ACK 中状态字段，例如 status").advanced().group("写入确认"))
                .addField(ProtocolField.ext("ackCodePath", "状态码字段", "text", false, "code", "ACK 中状态码字段，例如 code").advanced().group("写入确认"))
                .addField(ProtocolField.ext("ackSuccessValues", "成功值", "text", false, "true,success,ok,done,0,200", "逗号分隔，ACK 字段命中这些值时认为成功").advanced().group("写入确认"))
                .addField(ProtocolField.ext("ackFailureValues", "失败值", "text", false, "false,fail,failed,error,-1,500", "逗号分隔，ACK 字段命中这些值时认为失败").advanced().group("写入确认"))
                .addField(ProtocolField.ext("writeConfirmTimeoutMs", "写入确认超时", "number", false, 3000, "毫秒，等待设备回传新值").advanced().group("写入确认"))
                .addField(ProtocolField.ext("clientId", "客户端ID", "text", false, "", "为空时自动生成").advanced().group("连接参数"))
                .addField(ProtocolField.ext("username", "用户名", "text", false, "", "Broker 用户名").advanced().group("连接参数"))
                .addField(ProtocolField.ext("password", "密码", "text", false, "", "Broker 密码").advanced().group("连接参数"))
                .addField(ProtocolField.ext("staleTimeoutMs", "离线判定超时", "number", false, 5000, "毫秒，超过该时间未收到订阅数据则判定离线").advanced().group("连接参数"))
                .addField(ProtocolField.ext("cleanSession", "清理会话", "switch", false, true, "开启后断线不保留离线消息；关闭后需配合固定客户端ID和 QoS 1/2 使用").advanced().group("连接参数"))
                .addField(ProtocolField.ext("qos", "QoS", "number", false, 0, "0 / 1 / 2").advanced().group("连接参数"));
    }

    @Override
    public boolean connect(String ip, int port, Map<String, Object> extParams) {
        try {
            this.configuredExtParams = extParams == null ? new HashMap<>() : new HashMap<>(extParams);
            if (extParams != null && extParams.containsKey("topic")) {
                this.subTopic = (String) extParams.get("topic");
            }
            if (extParams != null && extParams.containsKey("publishTopic")) {
                this.publishTopic = String.valueOf(extParams.get("publishTopic"));
            }
            if (extParams != null && extParams.containsKey("ackTopic")) {
                this.ackTopic = String.valueOf(extParams.get("ackTopic"));
            }
            this.writePayloadTemplate = valueOf(extParams, "writePayloadTemplate");
            this.ackMatchMode = valueOrDefault(extParams, "ackMatchMode", "REQUEST_ID_OR_VALUE").trim().toUpperCase();
            this.ackRequestIdPath = valueOrDefault(extParams, "ackRequestIdPath", "requestId");
            this.ackSuccessPath = valueOrDefault(extParams, "ackSuccessPath", "success");
            this.ackStatusPath = valueOrDefault(extParams, "ackStatusPath", "status");
            this.ackCodePath = valueOrDefault(extParams, "ackCodePath", "code");
            this.ackPointKeyPath = valueOrDefault(extParams, "ackPointKeyPath", "pointKey");
            this.ackAddressPath = valueOrDefault(extParams, "ackAddressPath", "address");
            this.ackValuePath = valueOrDefault(extParams, "ackValuePath", "value");
            this.ackSuccessValues = valueOrDefault(extParams, "ackSuccessValues", "true,success,ok,done,0,200");
            this.ackFailureValues = valueOrDefault(extParams, "ackFailureValues", "false,fail,failed,error,-1,500");
            if (extParams != null && extParams.containsKey("qos")) {
                this.qos = Integer.parseInt(String.valueOf(extParams.get("qos")));
            }
            if (extParams != null && extParams.containsKey("cleanSession")) {
                this.cleanSession = Boolean.parseBoolean(String.valueOf(extParams.get("cleanSession")));
            }
            if (extParams != null && extParams.containsKey("writeConfirmTimeoutMs")) {
                this.writeConfirmTimeoutMs = Math.max(0, Integer.parseInt(String.valueOf(extParams.get("writeConfirmTimeoutMs"))));
            }
            if (extParams != null && extParams.containsKey("staleTimeoutMs")) {
                this.staleTimeoutMs = Math.max(1000, Integer.parseInt(String.valueOf(extParams.get("staleTimeoutMs"))));
            }

            String brokerUrl = "tcp://" + ip + ":" + port;
            String deviceKey = valueOrDefault(extParams, "_deviceId", "shared");
            String clientId = valueOf(extParams, "clientId");
            if (clientId == null || clientId.trim().length() == 0) {
                clientId = "Gateway_MQTT_" + Math.abs((brokerUrl + "|" + subTopic + "|" + deviceKey).hashCode());
            }

            String clientKey = deviceKey + "|" + brokerUrl + "|" + subTopic + "|" + clientId;
            latestDataCache = CACHES.computeIfAbsent(clientKey, key -> new ConcurrentHashMap<>());
            writeAckCache = ACK_CACHES.computeIfAbsent(clientKey, key -> new ConcurrentHashMap<>());
            mqttClient = CLIENTS.get(clientKey);
            if (mqttClient != null && mqttClient.isConnected()) {
                subscribeAckTopicIfNeeded();
                return true;
            }

            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(cleanSession);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            String username = valueOf(extParams, "username");
            String password = valueOf(extParams, "password");
            if (username != null && username.trim().length() > 0) {
                options.setUserName(username);
            }
            if (password != null && password.length() > 0) {
                options.setPassword(password.toCharArray());
            }

            // 设置回调，死死盯住设备发来的数据
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.err.println("⚠️ MQTT 连接断开");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    if (handleAckMessage(topic, payload)) {
                        return;
                    }
                    try {
                        Map<String, Object> parsedMap = parsePayload(payload);
                        latestDataCache.clear();
                        latestDataCache.putAll(parsedMap);
                        latestDataCache.put("_topic", topic);
                        latestDataCache.put("last_update", System.currentTimeMillis());
                    } catch (Exception e) {
                        Map<String, Object> looseMap = canParseAsLoose(payload) ? parseLoosePayload(payload) : new HashMap<>();
                        latestDataCache.clear();
                        if (looseMap.isEmpty()) {
                            latestDataCache.put("raw_payload", payload);
                        } else {
                            latestDataCache.putAll(looseMap);
                        }
                        latestDataCache.put("_topic", topic);
                        latestDataCache.put("last_update", System.currentTimeMillis());
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            mqttClient.connect(options);
            mqttClient.subscribe(subTopic, qos);
            subscribeAckTopicIfNeeded();
            CLIENTS.put(clientKey, mqttClient);
            System.out.println("✅ 成功建立 MQTT 连接并订阅: " + subTopic);
            return true;

        } catch (Exception e) {
            System.err.println("❌ MQTT 连接失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> readData(List<IotCommPoint> pointList) {
        // 直接返回缓存里的最新数据
        Long lastUpdate = parseLong(latestDataCache.get("last_update"));
        if (latestDataCache.isEmpty() || lastUpdate == null) {
            Map<String, Object> emptyMap = new HashMap<>();
            emptyMap.put("_collectStatus", "error");
            emptyMap.put("_errorMessage", "MQTT 已连接，但订阅主题尚未收到数据");
            return emptyMap;
        }
        long silentMs = System.currentTimeMillis() - lastUpdate;
        if (silentMs > staleTimeoutMs) {
            Map<String, Object> staleMap = new HashMap<>();
            staleMap.put("_collectStatus", "error");
            staleMap.put("_errorMessage", "MQTT 订阅主题超过 " + staleTimeoutMs + "ms 未收到数据");
            staleMap.put("last_update", lastUpdate);
            return staleMap;
        }
        Map<String, Object> result = new HashMap<>(latestDataCache);
        result.put("_collectStatus", "online");
        return result;
    }

    @Override
    public boolean writeData(IotCommPoint point, Object value) {
        if (mqttClient == null || !mqttClient.isConnected() || publishTopic == null || publishTopic.trim().length() == 0) {
            return false;
        }
        try {
            String requestId = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis();
            Object writeValue = normalizeWriteValue(point, value);
            String topic = renderTemplate(publishTopic, requestId, point, writeValue, timestamp);
            String payload = buildWritePayload(requestId, point, writeValue, timestamp);
            writeAckCache.remove(requestId);
            writeAckCache.remove("_latest");
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            mqttClient.publish(topic, message);
            return waitForWriteConfirm(requestId, point, writeValue, timestamp);
        } catch (Exception e) {
            System.err.println("MQTT write failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        // MQTT 订阅需要保持长连接，采集轮次结束时不主动断开。
    }

    private String valueOf(Map<String, Object> extParams, String key) {
        if (extParams == null || !extParams.containsKey(key) || extParams.get(key) == null) {
            return null;
        }
        return String.valueOf(extParams.get(key));
    }

    private String valueOrDefault(Map<String, Object> extParams, String key, String defaultValue) {
        String value = valueOf(extParams, key);
        return isBlank(value) ? defaultValue : value;
    }

    private Map<String, Object> parsePayload(String payload) {
        Object parsed = JSON.parse(payload);
        Map<String, Object> result = new HashMap<>();
        flatten("", parsed, result);
        return result;
    }

    private String buildWritePayload(String requestId, IotCommPoint point, Object value, long timestamp) {
        if (!isBlank(writePayloadTemplate)) {
            return renderTemplate(writePayloadTemplate, requestId, point, value, timestamp);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("pointKey", point.getPointKey());
        payload.put("address", point.getAddress());
        payload.put("dataType", point.getDataType());
        payload.put("value", value);
        payload.put("timestamp", timestamp);
        putIfConfigured(payload, "deviceCode");
        putIfConfigured(payload, "projectCode");
        return JSON.toJSONString(payload);
    }

    private Object normalizeWriteValue(IotCommPoint point, Object value) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.containsKey("value")) {
                return normalizeWriteValue(point, map.get("value"));
            }
            if (map.size() == 1) {
                return normalizeWriteValue(point, map.values().iterator().next());
            }
        }
        if (value == null) {
            return null;
        }
        String dataType = point.getDataType() == null ? "" : point.getDataType().trim();
        if ("Boolean".equalsIgnoreCase(dataType)) {
            if (value instanceof Boolean) {
                return value;
            }
            String text = String.valueOf(value).trim();
            return "true".equalsIgnoreCase(text) || "1".equals(text) || "on".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
        }
        if ("Float32".equalsIgnoreCase(dataType) || dataType.toLowerCase().contains("float")
                || dataType.toLowerCase().contains("double")) {
            return Double.parseDouble(String.valueOf(value).trim());
        }
        if (dataType.toLowerCase().contains("int")) {
            return Long.parseLong(String.valueOf(value).trim());
        }
        return value;
    }

    private void putIfConfigured(Map<String, Object> payload, String key) {
        Object value = configuredExtParams.get(key);
        if (value != null && !isBlank(String.valueOf(value))) {
            payload.put(key, value);
        }
    }

    private String renderTemplate(String template, String requestId, IotCommPoint point, Object value, long timestamp) {
        if (template == null) {
            return "";
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("requestId", requestId);
        vars.put("pointId", point.getId() == null ? "" : String.valueOf(point.getId()));
        vars.put("deviceId", point.getCommDeviceId() == null ? "" : String.valueOf(point.getCommDeviceId()));
        vars.put("pointKey", safe(point.getPointKey()));
        vars.put("pointLabel", safe(point.getPointLabel()));
        vars.put("address", safe(point.getAddress()));
        vars.put("dataType", safe(point.getDataType()));
        vars.put("value", value == null ? "" : String.valueOf(value));
        vars.put("valueJson", JSON.toJSONString(value));
        vars.put("timestamp", String.valueOf(timestamp));
        vars.put("deviceCode", safe(valueOf(configuredExtParams, "deviceCode")));
        vars.put("projectCode", safe(valueOf(configuredExtParams, "projectCode")));

        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private void subscribeAckTopicIfNeeded() throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected() || isBlank(ackTopic) || ackTopic.equals(subTopic)) {
            return;
        }
        mqttClient.subscribe(ackTopic, qos);
    }

    private boolean handleAckMessage(String topic, String payload) {
        if (isBlank(ackTopic) || !isTopicMatched(ackTopic, topic)) {
            return false;
        }
        try {
            Map<String, Object> ack = parsePayload(payload);
            ack.put("_ackTopic", topic);
            ack.put("_ackTime", System.currentTimeMillis());
            Object requestId = readPath(ack, ackRequestIdPath);
            if (requestId != null) {
                writeAckCache.put(String.valueOf(requestId), ack);
            }
            writeAckCache.put("_latest", ack);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void flatten(String prefix, Object value, Map<String, Object> result) {
        if (value == null) {
            return;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                String key = prefix.length() == 0 ? entry.getKey() : prefix + "." + entry.getKey();
                flatten(key, entry.getValue(), result);
            }
            return;
        }
        if (value instanceof Map) {
            Map<?, ?> object = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                String key = prefix.length() == 0 ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
                flatten(key, entry.getValue(), result);
            }
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++) {
                flatten(prefix + "[" + i + "]", array.get(i), result);
            }
            return;
        }
        if (value instanceof List) {
            List<?> array = (List<?>) value;
            for (int i = 0; i < array.size(); i++) {
                flatten(prefix + "[" + i + "]", array.get(i), result);
            }
            return;
        }
        if (prefix.length() > 0) {
            result.put(prefix, value);
        }
    }

    private boolean waitForWriteConfirm(String requestId, IotCommPoint point, Object expectedValue, long writeStartTime) {
        if (writeConfirmTimeoutMs <= 0 || isBlank(ackTopic)) {
            return true;
        }
        long deadline = System.currentTimeMillis() + writeConfirmTimeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Map<String, Object> ack = writeAckCache.remove(requestId);
            if (ack != null && isAckForWrite(ack, requestId, point, expectedValue, writeStartTime)) {
                return isAckSuccess(ack);
            }
            Map<String, Object> latestAck = writeAckCache.get("_latest");
            if (latestAck != null && isAckForWrite(latestAck, requestId, point, expectedValue, writeStartTime)) {
                writeAckCache.remove("_latest");
                return isAckSuccess(latestAck);
            }
            if (valueMatches(latestDataCache.get(point.getPointKey()), expectedValue)
                    || valueMatches(latestDataCache.get(point.getAddress()), expectedValue)) {
                return true;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        writeAckCache.remove(requestId);
        System.err.println("MQTT write confirm timeout, requestId=" + requestId + ", pointKey=" + point.getPointKey() + ", expected=" + expectedValue);
        return false;
    }

    private boolean isAckForWrite(Map<String, Object> ack, String requestId, IotCommPoint point, Object expectedValue, long writeStartTime) {
        Long ackTime = parseLong(ack.get("_ackTime"));
        if (ackTime != null && ackTime < writeStartTime) {
            return false;
        }
        if ("ANY_SUCCESS".equalsIgnoreCase(ackMatchMode)) {
            return true;
        }

        Object ackRequestId = readPath(ack, ackRequestIdPath);
        if (ackRequestId != null && String.valueOf(ackRequestId).equals(requestId)) {
            return true;
        }
        if ("REQUEST_ID".equalsIgnoreCase(ackMatchMode)) {
            return false;
        }

        Object ackPointKey = readPath(ack, ackPointKeyPath);
        Object ackAddress = readPath(ack, ackAddressPath);
        boolean pointMatched = (ackPointKey != null && String.valueOf(ackPointKey).equals(point.getPointKey()))
                || (ackAddress != null && String.valueOf(ackAddress).equals(point.getAddress()));
        if (!pointMatched) {
            return false;
        }
        if ("POINT_VALUE".equalsIgnoreCase(ackMatchMode) || "REQUEST_ID_OR_VALUE".equalsIgnoreCase(ackMatchMode)) {
            Object ackValue = readPath(ack, ackValuePath);
            return ackValue == null || valueMatches(ackValue, expectedValue);
        }
        return pointMatched;
    }

    private boolean isAckSuccess(Map<String, Object> ack) {
        Object success = readPath(ack, ackSuccessPath);
        if (success != null) {
            String text = String.valueOf(success);
            if (containsToken(ackSuccessValues, text)) {
                return true;
            }
            if (containsToken(ackFailureValues, text)) {
                return false;
            }
        }

        Object status = readPath(ack, ackStatusPath);
        if (status != null) {
            String text = String.valueOf(status);
            if (containsToken(ackSuccessValues, text)) {
                return true;
            }
            if (containsToken(ackFailureValues, text)) {
                return false;
            }
        }

        Object code = readPath(ack, ackCodePath);
        if (code != null) {
            String text = String.valueOf(code);
            return containsToken(ackSuccessValues, text);
        }
        return true;
    }

    private boolean valueMatches(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number || expected instanceof Number) {
            try {
                double left = Double.parseDouble(String.valueOf(actual));
                double right = Double.parseDouble(String.valueOf(expected));
                return Math.abs(left - right) < 0.000001D;
            } catch (Exception ignored) {
                return false;
            }
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private Object readPath(Map<String, Object> map, String path) {
        if (map == null || isBlank(path)) {
            return null;
        }
        if (map.containsKey(path)) {
            return map.get(path);
        }
        return null;
    }

    private boolean containsToken(String csv, String value) {
        if (value == null) {
            return false;
        }
        String expected = value.trim();
        if (expected.length() == 0) {
            return false;
        }
        String[] tokens = (csv == null ? "" : csv).split(",");
        for (String token : tokens) {
            if (expected.equalsIgnoreCase(token.trim())) {
                return true;
            }
        }
        return false;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isTopicMatched(String filter, String topic) {
        if (filter == null || topic == null) {
            return false;
        }
        if (filter.equals(topic)) {
            return true;
        }

        String[] filterParts = filter.split("/");
        String[] topicParts = topic.split("/");
        int topicIndex = 0;
        for (int filterIndex = 0; filterIndex < filterParts.length; filterIndex++) {
            String part = filterParts[filterIndex];
            if ("#".equals(part)) {
                return filterIndex == filterParts.length - 1;
            }
            if (topicIndex >= topicParts.length) {
                return false;
            }
            if (!"+".equals(part) && !part.equals(topicParts[topicIndex])) {
                return false;
            }
            topicIndex++;
        }
        return topicIndex == topicParts.length;
    }

    private Map<String, Object> parseLoosePayload(String payload) {
        Map<String, Object> result = new HashMap<>();
        if (payload == null) {
            return result;
        }
        String[] segments = payload.split("[,;\\n\\r]+");
        for (String segment : segments) {
            String text = segment == null ? "" : segment.trim();
            if (text.length() == 0) {
                continue;
            }
            int splitIndex = text.indexOf('=');
            if (splitIndex < 0) {
                splitIndex = text.indexOf(':');
            }
            if (splitIndex <= 0 || splitIndex >= text.length() - 1) {
                continue;
            }
            String key = text.substring(0, splitIndex).trim();
            String value = text.substring(splitIndex + 1).trim();
            if (key.length() > 0) {
                result.put(key, parseScalar(value));
            }
        }
        return result;
    }

    private boolean canParseAsLoose(String payload) {
        if (payload == null) {
            return false;
        }
        String text = payload.trim();
        if (text.startsWith("{") || text.startsWith("[")) {
            return false;
        }
        return true;
    }

    private Object parseScalar(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        try {
            if (text.contains(".")) {
                return Double.parseDouble(text);
            }
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return text;
        }
    }
}
