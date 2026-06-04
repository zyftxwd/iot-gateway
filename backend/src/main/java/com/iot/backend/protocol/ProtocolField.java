package com.iot.backend.protocol;

import java.util.ArrayList;
import java.util.List;

public class ProtocolField {

    private String key;
    private String label;
    private String type;
    private String target;
    private Boolean required;
    private Object defaultValue;
    private String placeholder;
    private Boolean advanced;
    private String groupName;
    private List<ProtocolFieldOption> options = new ArrayList<>();

    public ProtocolField() {
    }

    public ProtocolField(String key, String label, String type, String target, Boolean required, Object defaultValue, String placeholder) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.target = target;
        this.required = required;
        this.defaultValue = defaultValue;
        this.placeholder = placeholder;
        this.advanced = false;
        this.groupName = "基础参数";
    }

    public static ProtocolField device(String key, String label, String type, Boolean required, Object defaultValue, String placeholder) {
        return new ProtocolField(key, label, type, "DEVICE", required, defaultValue, placeholder);
    }

    public static ProtocolField ext(String key, String label, String type, Boolean required, Object defaultValue, String placeholder) {
        return new ProtocolField(key, label, type, "EXT_CONFIG", required, defaultValue, placeholder);
    }

    public ProtocolField advanced() {
        this.advanced = true;
        this.groupName = "高级参数";
        return this;
    }

    public ProtocolField group(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public Boolean getAdvanced() {
        return advanced;
    }

    public void setAdvanced(Boolean advanced) {
        this.advanced = advanced;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<ProtocolFieldOption> getOptions() {
        return options;
    }

    public void setOptions(List<ProtocolFieldOption> options) {
        this.options = options;
    }
}
