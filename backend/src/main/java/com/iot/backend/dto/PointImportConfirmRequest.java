package com.iot.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class PointImportConfirmRequest {
    private String duplicateStrategy;
    private List<PointImportPreviewRow> rows = new ArrayList<>();

    public String getDuplicateStrategy() {
        return duplicateStrategy;
    }

    public void setDuplicateStrategy(String duplicateStrategy) {
        this.duplicateStrategy = duplicateStrategy;
    }

    public List<PointImportPreviewRow> getRows() {
        return rows;
    }

    public void setRows(List<PointImportPreviewRow> rows) {
        this.rows = rows;
    }
}
