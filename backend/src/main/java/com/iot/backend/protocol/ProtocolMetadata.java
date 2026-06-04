package com.iot.backend.protocol;

import java.util.ArrayList;
import java.util.List;

public class ProtocolMetadata {

    private String protocolType;
    private String displayName;
    private String description;
    private Boolean enabled;
    private String installStatus;
    private String version;
    private Boolean supportsDiscovery;
    private Boolean supportsWrite;
    private Boolean supportsExcelImport;
    private Boolean supportsJsonImport;
    private Boolean supportsBrowse;
    private List<ProtocolField> fields = new ArrayList<>();

    public ProtocolMetadata() {
    }

    public ProtocolMetadata(String protocolType, String displayName, String description, Boolean enabled) {
        this.protocolType = protocolType;
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
        this.installStatus = enabled ? "ENABLED" : "DISABLED";
        this.version = "1.0.0";
        this.supportsDiscovery = false;
        this.supportsWrite = false;
        this.supportsExcelImport = false;
        this.supportsJsonImport = false;
        this.supportsBrowse = false;
    }

    public ProtocolMetadata addField(ProtocolField field) {
        this.fields.add(field);
        return this;
    }

    public ProtocolMetadata capabilities(Boolean supportsDiscovery, Boolean supportsWrite,
                                         Boolean supportsExcelImport, Boolean supportsJsonImport,
                                         Boolean supportsBrowse) {
        this.supportsDiscovery = supportsDiscovery;
        this.supportsWrite = supportsWrite;
        this.supportsExcelImport = supportsExcelImport;
        this.supportsJsonImport = supportsJsonImport;
        this.supportsBrowse = supportsBrowse;
        return this;
    }

    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getInstallStatus() {
        return installStatus;
    }

    public void setInstallStatus(String installStatus) {
        this.installStatus = installStatus;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<ProtocolField> getFields() {
        return fields;
    }

    public void setFields(List<ProtocolField> fields) {
        this.fields = fields;
    }

    public Boolean getSupportsDiscovery() {
        return supportsDiscovery;
    }

    public void setSupportsDiscovery(Boolean supportsDiscovery) {
        this.supportsDiscovery = supportsDiscovery;
    }

    public Boolean getSupportsWrite() {
        return supportsWrite;
    }

    public void setSupportsWrite(Boolean supportsWrite) {
        this.supportsWrite = supportsWrite;
    }

    public Boolean getSupportsExcelImport() {
        return supportsExcelImport;
    }

    public void setSupportsExcelImport(Boolean supportsExcelImport) {
        this.supportsExcelImport = supportsExcelImport;
    }

    public Boolean getSupportsJsonImport() {
        return supportsJsonImport;
    }

    public void setSupportsJsonImport(Boolean supportsJsonImport) {
        this.supportsJsonImport = supportsJsonImport;
    }

    public Boolean getSupportsBrowse() {
        return supportsBrowse;
    }

    public void setSupportsBrowse(Boolean supportsBrowse) {
        this.supportsBrowse = supportsBrowse;
    }
}
