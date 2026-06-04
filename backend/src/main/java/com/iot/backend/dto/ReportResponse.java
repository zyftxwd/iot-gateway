package com.iot.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ReportResponse {
    private String reportType;
    private String title;
    private String subtitle;
    private Long generatedAt;
    private List<Metric> metrics = new ArrayList<>();
    private List<Chart> charts = new ArrayList<>();
    private List<Column> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();

    @Data
    public static class Metric {
        private String key;
        private String label;
        private String value;
        private String unit;
        private String level;
    }

    @Data
    public static class Chart {
        private String key;
        private String title;
        private String type;
        private List<Map<String, Object>> data = new ArrayList<>();
    }

    @Data
    public static class Column {
        private String key;
        private String label;
        private Integer width;
        private String align;
    }
}
