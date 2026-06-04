package com.iot.backend.protocol;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Protocol router.
 *
 * Protocol handlers hold connection state, so the factory stores handler classes
 * and returns a fresh Spring bean for each collection or write operation.
 */
@Component
public class ProtocolHandlerFactory {

    @Autowired
    private List<IProtocolHandler> handlerList;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${industrial.protocol.definition-file:./protocol-definitions.json}")
    private String protocolDefinitionFile;

    private final Map<String, Class<? extends IProtocolHandler>> handlerMap = new HashMap<>();
    private final Map<String, ProtocolMetadata> metadataMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (IProtocolHandler handler : handlerList) {
            handlerMap.put(handler.getProtocolType(), handler.getClass());
            ProtocolMetadata metadata = handler.metadata();
            if (metadata != null) {
                metadataMap.put(handler.getProtocolType(), metadata);
            }
            System.out.println("protocol handler loaded: " + handler.getProtocolType());
        }
        loadExternalDefinitions();
    }

    public IProtocolHandler getHandler(String protocolType) {
        Class<? extends IProtocolHandler> handlerClass = handlerMap.get(protocolType);
        if (handlerClass == null) {
            throw new RuntimeException("protocol handler not installed: " + protocolType);
        }
        return applicationContext.getBean(handlerClass);
    }

    public List<ProtocolMetadata> listEnabledProtocols() {
        List<ProtocolMetadata> protocols = new ArrayList<>();
        for (ProtocolMetadata metadata : metadataMap.values()) {
            if (Boolean.TRUE.equals(metadata.getEnabled())) {
                protocols.add(metadata);
            }
        }
        Collections.sort(protocols, Comparator.comparing(ProtocolMetadata::getProtocolType));
        return protocols;
    }

    public List<ProtocolMetadata> listInstalledProtocols() {
        List<ProtocolMetadata> protocols = new ArrayList<>(metadataMap.values());
        Collections.sort(protocols, Comparator.comparing(ProtocolMetadata::getProtocolType));
        return protocols;
    }

    public ProtocolMetadata importDefinition(ProtocolMetadata metadata) {
        ProtocolMetadata normalized = normalizeExternalDefinition(metadata);
        metadataMap.put(normalized.getProtocolType(), normalized);
        persistExternalDefinitions();
        return normalized;
    }

    public boolean isEnabledProtocol(String protocolType) {
        if (protocolType == null) {
            return false;
        }
        ProtocolMetadata metadata = metadataMap.get(protocolType.trim().toUpperCase());
        return metadata != null && Boolean.TRUE.equals(metadata.getEnabled());
    }

    public ProtocolMetadata getMetadata(String protocolType) {
        if (protocolType == null) {
            return null;
        }
        return metadataMap.get(protocolType.trim().toUpperCase());
    }

    public int defaultPort(String protocolType) {
        ProtocolMetadata metadata = getMetadata(protocolType);
        if (metadata == null || metadata.getFields() == null) {
            return 0;
        }
        for (ProtocolField field : metadata.getFields()) {
            if ("DEVICE".equals(field.getTarget()) && "port".equals(field.getKey()) && field.getDefaultValue() != null) {
                try {
                    return Integer.parseInt(String.valueOf(field.getDefaultValue()));
                } catch (Exception ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private void loadExternalDefinitions() {
        try {
            File file = new File(protocolDefinitionFile);
            if (!file.exists()) {
                return;
            }
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<ProtocolMetadata> definitions = JSON.parseArray(text, ProtocolMetadata.class);
            if (definitions == null) {
                return;
            }
            for (ProtocolMetadata definition : definitions) {
                ProtocolMetadata normalized = normalizeExternalDefinition(definition);
                if (!handlerMap.containsKey(normalized.getProtocolType())) {
                    metadataMap.put(normalized.getProtocolType(), normalized);
                }
            }
        } catch (Exception e) {
            System.out.println("load external protocol definitions failed: " + e.getMessage());
        }
    }

    private void persistExternalDefinitions() {
        List<ProtocolMetadata> external = new ArrayList<>();
        for (ProtocolMetadata metadata : metadataMap.values()) {
            if (!handlerMap.containsKey(metadata.getProtocolType())) {
                external.add(metadata);
            }
        }
        Collections.sort(external, Comparator.comparing(ProtocolMetadata::getProtocolType));
        File file = new File(protocolDefinitionFile);
        File parent = file.getParentFile();
        try {
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), JSON.toJSONString(external).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("save protocol definition failed: " + e.getMessage(), e);
        }
    }

    private ProtocolMetadata normalizeExternalDefinition(ProtocolMetadata metadata) {
        if (metadata == null || metadata.getProtocolType() == null || metadata.getProtocolType().trim().length() == 0) {
            throw new IllegalArgumentException("protocolType is required");
        }
        String protocolType = metadata.getProtocolType().trim().toUpperCase();
        if (handlerMap.containsKey(protocolType)) {
            throw new IllegalArgumentException("built-in protocol cannot be overwritten: " + protocolType);
        }
        metadata.setProtocolType(protocolType);
        if (metadata.getDisplayName() == null || metadata.getDisplayName().trim().length() == 0) {
            metadata.setDisplayName(protocolType);
        }
        if (metadata.getVersion() == null || metadata.getVersion().trim().length() == 0) {
            metadata.setVersion("1.0.0");
        }
        metadata.setEnabled(false);
        metadata.setInstallStatus("CONFIG_ONLY");
        metadata.setSupportsDiscovery(Boolean.TRUE.equals(metadata.getSupportsDiscovery()));
        metadata.setSupportsWrite(Boolean.TRUE.equals(metadata.getSupportsWrite()));
        metadata.setSupportsExcelImport(Boolean.TRUE.equals(metadata.getSupportsExcelImport()));
        metadata.setSupportsJsonImport(Boolean.TRUE.equals(metadata.getSupportsJsonImport()));
        metadata.setSupportsBrowse(Boolean.TRUE.equals(metadata.getSupportsBrowse()));
        if (metadata.getFields() == null) {
            metadata.setFields(new ArrayList<>());
        }
        for (ProtocolField field : metadata.getFields()) {
            if (field.getTarget() == null || field.getTarget().trim().length() == 0) {
                field.setTarget("EXT_CONFIG");
            }
            if (field.getType() == null || field.getType().trim().length() == 0) {
                field.setType("TEXT");
            }
            if (field.getAdvanced() == null) {
                field.setAdvanced(false);
            }
            if (field.getGroupName() == null || field.getGroupName().trim().length() == 0) {
                field.setGroupName(Boolean.TRUE.equals(field.getAdvanced()) ? "高级参数" : "基础参数");
            }
        }
        return metadata;
    }
}
