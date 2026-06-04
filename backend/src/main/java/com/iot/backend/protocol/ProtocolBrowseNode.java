package com.iot.backend.protocol;

import java.util.ArrayList;
import java.util.List;

public class ProtocolBrowseNode {
    private String id;
    private String label;
    private String nodeId;
    private String nodeClass;
    private String dataType;
    private Object sampleValue;
    private Boolean variable;
    private Boolean writable;
    private Boolean disabled;
    private List<ProtocolBrowseNode> children = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeClass() {
        return nodeClass;
    }

    public void setNodeClass(String nodeClass) {
        this.nodeClass = nodeClass;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Object getSampleValue() {
        return sampleValue;
    }

    public void setSampleValue(Object sampleValue) {
        this.sampleValue = sampleValue;
    }

    public Boolean getVariable() {
        return variable;
    }

    public void setVariable(Boolean variable) {
        this.variable = variable;
    }

    public Boolean getWritable() {
        return writable;
    }

    public void setWritable(Boolean writable) {
        this.writable = writable;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public List<ProtocolBrowseNode> getChildren() {
        return children;
    }

    public void setChildren(List<ProtocolBrowseNode> children) {
        this.children = children;
    }
}
