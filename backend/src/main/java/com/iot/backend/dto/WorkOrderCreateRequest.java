package com.iot.backend.dto;

public class WorkOrderCreateRequest {
    private Long projectId;
    private Long deviceId;
    private Long pointId;
    private String title;
    private String description;
    private String priority;
    private Long assigneeUserId;
    private Long verifierUserId;
    private Long plannedFinishTime;
    private String remark;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Long getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public Long getVerifierUserId() {
        return verifierUserId;
    }

    public void setVerifierUserId(Long verifierUserId) {
        this.verifierUserId = verifierUserId;
    }

    public Long getPlannedFinishTime() {
        return plannedFinishTime;
    }

    public void setPlannedFinishTime(Long plannedFinishTime) {
        this.plannedFinishTime = plannedFinishTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
