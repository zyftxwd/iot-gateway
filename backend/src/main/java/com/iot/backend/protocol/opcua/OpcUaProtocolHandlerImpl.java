package com.iot.backend.protocol.opcua;

import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.protocol.IProtocolHandler;
import com.iot.backend.protocol.ProtocolBrowseNode;
import com.iot.backend.protocol.ProtocolField;
import com.iot.backend.protocol.ProtocolFieldOption;
import com.iot.backend.protocol.ProtocolMetadata;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OpcUaProtocolHandlerImpl implements IProtocolHandler {

    private OpcUaClient client;
    private boolean connected;

    @Override
    public String getProtocolType() {
        return "OPC_UA";
    }

    @Override
    public ProtocolMetadata metadata() {
        ProtocolField securityPolicy = ProtocolField.ext("securityPolicy", "安全策略", "select", false, "None", "默认 None");
        securityPolicy.getOptions().add(new ProtocolFieldOption("None", "None"));

        ProtocolField authMode = ProtocolField.ext("authMode", "认证方式", "select", false, "ANONYMOUS", "匿名或用户名密码");
        authMode.getOptions().add(new ProtocolFieldOption("匿名", "ANONYMOUS"));
        authMode.getOptions().add(new ProtocolFieldOption("用户名密码", "USERNAME"));

        return new ProtocolMetadata("OPC_UA", "OPC UA", "OPC UA 客户端采集，点位地址填写标准 NodeId，例如 ns=2;s=Machine.Temperature。", true)
                .capabilities(true, true, false, true, true)
                .addField(ProtocolField.device("ipAddress", "服务器地址", "text", true, "127.0.0.1", "OPC UA Server IP"))
                .addField(ProtocolField.device("port", "端口", "number", true, 4840, "默认 4840"))
                .addField(securityPolicy)
                .addField(authMode)
                .addField(ProtocolField.ext("username", "用户名", "text", false, "", "认证方式为用户名密码时填写").advanced().group("认证参数"))
                .addField(ProtocolField.ext("password", "密码", "password", false, "", "认证方式为用户名密码时填写").advanced().group("认证参数"))
                .addField(ProtocolField.ext("endpointUrl", "完整端点", "text", false, "", "为空时按地址和端口拼接").advanced().group("连接参数"))
                .addField(ProtocolField.ext("requestTimeoutMs", "请求超时", "number", false, 5000, "毫秒").advanced().group("连接参数"));
    }

    @Override
    public boolean connect(String ip, int port, Map<String, Object> extParams) {
        String endpointUrl = valueOrDefault(extParams, "endpointUrl", "");
        if (endpointUrl.length() == 0) {
            endpointUrl = "opc.tcp://" + ip + ":" + port;
        }

        try {
            List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(endpointUrl).get();
            EndpointDescription endpoint = chooseEndpoint(endpoints, valueOrDefault(extParams, "securityPolicy", "None"));
            if (endpoint == null) {
                System.err.println("OPC UA endpoint not found: " + endpointUrl);
                connected = false;
                return false;
            }

            long timeout = longValue(extParams, "requestTimeoutMs", 5000L);
            OpcUaClientConfigBuilder builder = OpcUaClientConfig.builder()
                    .setApplicationName(LocalizedText.english("IIoT Gateway OPC UA Client"))
                    .setApplicationUri("urn:iiot-gateway:opcua-client")
                    .setEndpoint(endpoint)
                    .setRequestTimeout(Unsigned.uint(timeout));

            if ("USERNAME".equalsIgnoreCase(valueOrDefault(extParams, "authMode", "ANONYMOUS"))) {
                builder.setIdentityProvider(new UsernameProvider(valueOrDefault(extParams, "username", ""), valueOrDefault(extParams, "password", "")));
            } else {
                builder.setIdentityProvider(new AnonymousProvider());
            }

            client = OpcUaClient.create(builder.build());
            client.connect().get();
            connected = true;
            return true;
        } catch (Exception e) {
            System.err.println("OPC UA connect failed [" + endpointUrl + "]: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    @Override
    public Map<String, Object> readData(List<IotCommPoint> pointList) {
        Map<String, Object> result = new HashMap<>();
        if (client == null || !connected) {
            result.put("status", "offline");
            return result;
        }

        if (pointList == null) {
            result.put("status", "online");
            return result;
        }

        for (IotCommPoint point : pointList) {
            if (point == null || point.getPointKey() == null || point.getAddress() == null || point.getAddress().trim().length() == 0) {
                continue;
            }
            try {
                DataValue dataValue = client.readValue(0.0, TimestampsToReturn.Both, NodeId.parse(point.getAddress().trim())).get();
                if (dataValue != null && dataValue.getStatusCode() != null && dataValue.getStatusCode().isGood() && dataValue.getValue() != null) {
                    result.put(point.getPointKey(), dataValue.getValue().getValue());
                }
            } catch (Exception e) {
                result.put(point.getPointKey(), null);
                result.put(point.getPointKey() + "_error", e.getMessage());
            }
        }
        result.put("status", "online");
        return result;
    }

    @Override
    public boolean writeData(IotCommPoint point, Object value) {
        if (client == null || !connected || point == null || point.getAddress() == null || point.getAddress().trim().length() == 0) {
            return false;
        }
        try {
            NodeId nodeId = NodeId.parse(point.getAddress().trim());
            DataValue currentValue = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get();
            Object currentRaw = currentValue == null || currentValue.getValue() == null ? null : currentValue.getValue().getValue();
            Object typedValue = convertWriteValue(point.getDataType(), value, currentRaw);
            DataValue dataValue = new DataValue(new Variant(typedValue));
            StatusCode statusCode = client.writeValue(nodeId, dataValue).get();
            return statusCode != null && statusCode.isGood();
        } catch (Exception e) {
            System.err.println("OPC UA write failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<ProtocolBrowseNode> browseNodes() {
        List<ProtocolBrowseNode> roots = new java.util.ArrayList<>();
        if (client == null || !connected) {
            return roots;
        }
        roots.add(browseNode(Identifiers.ObjectsFolder, "Objects", 0, new HashSet<>()));
        return roots;
    }

    @Override
    public void disconnect() {
        if (client != null) {
            try {
                client.disconnect().get();
            } catch (Exception ignored) {
            } finally {
                connected = false;
                client = null;
            }
        }
    }

    private EndpointDescription chooseEndpoint(List<EndpointDescription> endpoints, String securityPolicy) {
        if (endpoints == null || endpoints.isEmpty()) {
            return null;
        }
        String targetUri = "None".equalsIgnoreCase(securityPolicy)
                ? "http://opcfoundation.org/UA/SecurityPolicy#None"
                : securityPolicy;
        Optional<EndpointDescription> matched = endpoints.stream()
                .filter(e -> targetUri.equals(e.getSecurityPolicyUri()))
                .findFirst();
        return matched.orElse(endpoints.get(0));
    }

    private ProtocolBrowseNode browseNode(NodeId nodeId, String label, int depth, Set<String> visited) {
        ProtocolBrowseNode node = new ProtocolBrowseNode();
        node.setId(nodeId.toParseableString());
        node.setNodeId(nodeId.toParseableString());
        node.setLabel(label == null || label.trim().length() == 0 ? nodeId.toParseableString() : label);
        node.setVariable(false);
        node.setWritable(false);
        node.setDisabled(true);

        String visitKey = nodeId.toParseableString();
        if (depth >= 8 || visited.contains(visitKey)) {
            return node;
        }
        visited.add(visitKey);

        try {
            BrowseDescription browse = new BrowseDescription(
                    nodeId,
                    BrowseDirection.Forward,
                    Identifiers.HierarchicalReferences,
                    true,
                    UInteger.valueOf(3),
                    UInteger.valueOf(63)
            );
            ViewDescription view = new ViewDescription(NodeId.NULL_VALUE, DateTime.MIN_VALUE, UInteger.valueOf(0));
            BrowseResult[] results = client.browse(view, UInteger.valueOf(100), java.util.Collections.singletonList(browse)).get().getResults();
            if (results == null || results.length == 0 || results[0].getReferences() == null) {
                return node;
            }
            for (ReferenceDescription reference : results[0].getReferences()) {
                Optional<NodeId> localNodeId = toLocalNodeId(reference.getNodeId());
                if (!localNodeId.isPresent()) {
                    continue;
                }
                String childLabel = reference.getDisplayName() == null || reference.getDisplayName().getText() == null
                        ? String.valueOf(reference.getBrowseName().getName())
                        : reference.getDisplayName().getText();
                if (isOpcUaSystemBranch(nodeId, localNodeId.get(), childLabel)) {
                    continue;
                }
                ProtocolBrowseNode child = browseNode(localNodeId.get(), childLabel, depth + 1, visited);
                child.setNodeClass(reference.getNodeClass() == null ? null : reference.getNodeClass().name());
                if (NodeClass.Variable.equals(reference.getNodeClass())) {
                    fillVariableMetadata(child, localNodeId.get());
                }
                node.getChildren().add(child);
            }
        } catch (Exception ignored) {
            node.setDisabled(true);
        }
        return node;
    }

    private boolean isOpcUaSystemBranch(NodeId parentNodeId, NodeId childNodeId, String childLabel) {
        if (!Identifiers.ObjectsFolder.equals(parentNodeId)) {
            return false;
        }
        if (!childNodeId.getNamespaceIndex().equals(UShort.valueOf(0))) {
            return false;
        }
        return "Server".equalsIgnoreCase(childLabel)
                || "Aliases".equalsIgnoreCase(childLabel)
                || "Locations".equalsIgnoreCase(childLabel);
    }

    private void fillVariableMetadata(ProtocolBrowseNode node, NodeId nodeId) {
        node.setVariable(true);
        node.setDisabled(false);
        try {
            DataValue value = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get();
            Object raw = value == null || value.getValue() == null ? null : value.getValue().getValue();
            node.setSampleValue(raw);
            node.setDataType(inferPointType(raw));
            node.setWritable(true);
        } catch (Exception e) {
            node.setDataType("String");
        }
    }

    private Optional<NodeId> toLocalNodeId(ExpandedNodeId expandedNodeId) {
        try {
            if (expandedNodeId == null) {
                return Optional.empty();
            }
            if (expandedNodeId.isLocal()) {
                return expandedNodeId.local();
            }
            return expandedNodeId.local(client.getNamespaceTable());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String inferPointType(Object raw) {
        if (raw instanceof Boolean) {
            return "Boolean";
        }
        if (raw instanceof Float || raw instanceof Double) {
            return "Float32";
        }
        if (raw instanceof Number) {
            return "Int32";
        }
        return "String";
    }

    private Object convertWriteValue(String dataType, Object value, Object currentRaw) {
        if (value == null) {
            return null;
        }
        if (currentRaw instanceof Double) {
            return Double.parseDouble(String.valueOf(value));
        }
        if (currentRaw instanceof Float) {
            return Float.parseFloat(String.valueOf(value));
        }
        if (currentRaw instanceof Integer) {
            return Integer.parseInt(String.valueOf(value));
        }
        if (currentRaw instanceof Long) {
            return Long.parseLong(String.valueOf(value));
        }
        if (currentRaw instanceof Short) {
            return Short.parseShort(String.valueOf(value));
        }
        if (currentRaw instanceof Boolean) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        String type = dataType == null ? "String" : dataType;
        String text = String.valueOf(value);
        if ("Boolean".equalsIgnoreCase(type)) {
            return Boolean.parseBoolean(text);
        }
        if ("Int16".equalsIgnoreCase(type)) {
            return Short.parseShort(text);
        }
        if ("UInt16".equalsIgnoreCase(type) || "Int32".equalsIgnoreCase(type)) {
            return Integer.parseInt(text);
        }
        if ("UInt32".equalsIgnoreCase(type)) {
            return Long.parseLong(text);
        }
        if ("Float32".equalsIgnoreCase(type)) {
            return Float.parseFloat(text);
        }
        return text;
    }

    private String valueOrDefault(Map<String, Object> extParams, String key, String defaultValue) {
        if (extParams == null || !extParams.containsKey(key) || extParams.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(extParams.get(key)).trim();
        return value.length() == 0 ? defaultValue : value;
    }

    private long longValue(Map<String, Object> extParams, String key, long defaultValue) {
        try {
            return Long.parseLong(valueOrDefault(extParams, key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
