package com.iot.backend.dto;

import lombok.Data;

@Data
public class ReportSchemeRequest {
    private String reportType;
    private String schemeName;
    private String filtersJson;
    private String layoutJson;
}
