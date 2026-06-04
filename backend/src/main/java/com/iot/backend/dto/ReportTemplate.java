package com.iot.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReportTemplate {
    private String reportType;
    private String title;
    private String description;
    private List<String> supportedExports = new ArrayList<>();
}
