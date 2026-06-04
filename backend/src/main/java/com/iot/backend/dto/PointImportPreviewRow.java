package com.iot.backend.dto;

import com.iot.backend.entity.IotCommPoint;

import java.util.ArrayList;
import java.util.List;

public class PointImportPreviewRow {
    private Integer rowNumber;
    private IotCommPoint point;
    private Boolean valid;
    private Boolean duplicate;
    private Long existingPointId;
    private Object sampleValue;
    private List<String> errors = new ArrayList<>();

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public IotCommPoint getPoint() {
        return point;
    }

    public void setPoint(IotCommPoint point) {
        this.point = point;
    }

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public Boolean getDuplicate() {
        return duplicate;
    }

    public void setDuplicate(Boolean duplicate) {
        this.duplicate = duplicate;
    }

    public Long getExistingPointId() {
        return existingPointId;
    }

    public void setExistingPointId(Long existingPointId) {
        this.existingPointId = existingPointId;
    }

    public Object getSampleValue() {
        return sampleValue;
    }

    public void setSampleValue(Object sampleValue) {
        this.sampleValue = sampleValue;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
