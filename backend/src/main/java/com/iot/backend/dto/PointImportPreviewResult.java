package com.iot.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class PointImportPreviewResult {
    private int totalCount;
    private int validCount;
    private int invalidCount;
    private int duplicateCount;
    private List<PointImportPreviewRow> rows = new ArrayList<>();

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getValidCount() {
        return validCount;
    }

    public void setValidCount(int validCount) {
        this.validCount = validCount;
    }

    public int getInvalidCount() {
        return invalidCount;
    }

    public void setInvalidCount(int invalidCount) {
        this.invalidCount = invalidCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public List<PointImportPreviewRow> getRows() {
        return rows;
    }

    public void setRows(List<PointImportPreviewRow> rows) {
        this.rows = rows;
    }
}
