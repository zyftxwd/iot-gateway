package com.iot.backend.protocol;

public class ProtocolFieldOption {

    private String label;
    private Object value;

    public ProtocolFieldOption() {
    }

    public ProtocolFieldOption(String label, Object value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
