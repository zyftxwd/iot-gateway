package com.iot.backend.dto;

import lombok.Data;

@Data
public class ReportRequest {
    private String reportType;
    private Long projectId;
    private Long groupId;
    private Long deviceId;
    private Long pointId;
    private Long startTime;
    private Long endTime;
    private Integer limit;
}
