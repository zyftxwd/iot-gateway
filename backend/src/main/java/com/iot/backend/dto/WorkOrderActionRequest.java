package com.iot.backend.dto;

public class WorkOrderActionRequest {
    private String action;
    private Long assigneeUserId;
    private Long verifierUserId;
    private Long plannedFinishTime;
    private String faultType;
    private String faultReason;
    private String processMeasure;
    private String processResult;
    private String remark;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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

    public String getFaultType() {
        return faultType;
    }

    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }

    public String getFaultReason() {
        return faultReason;
    }

    public void setFaultReason(String faultReason) {
        this.faultReason = faultReason;
    }

    public String getProcessMeasure() {
        return processMeasure;
    }

    public void setProcessMeasure(String processMeasure) {
        this.processMeasure = processMeasure;
    }

    public String getProcessResult() {
        return processResult;
    }

    public void setProcessResult(String processResult) {
        this.processResult = processResult;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
