package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_maintenance_card")
public class IotMaintenanceCard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private Long alarmEventId;
    private Long projectId;
    private Long deviceId;
    private Long pointId;
    private String title;
    private String faultType;
    private String faultReason;
    private String processMeasure;
    private String processResult;
    private String keywords;
    private String tags;
    private Long creatorUserId;
    private String creatorName;
    private Long createTime;
}
