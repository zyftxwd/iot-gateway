package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_work_order")
public class IotWorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String sourceType;
    private Long alarmEventId;
    private Long projectId;
    private Long deviceId;
    private Long pointId;
    private String title;
    private String description;
    private String faultType;
    private String faultReason;
    private String processMeasure;
    private String processResult;
    private String priority;
    private String status;
    private String flowKey;
    private Long creatorUserId;
    private String creatorName;
    private Long assigneeUserId;
    private String assigneeName;
    private Long verifierUserId;
    private String verifierName;
    private Long deptId;
    private Long plannedFinishTime;
    private Long createTime;
    private Long dispatchTime;
    private Long acceptTime;
    private Long processTime;
    private Long finishTime;
    private Long verifyTime;
    private Long closeTime;
    private Long archiveCardId;
    private String remark;
}
