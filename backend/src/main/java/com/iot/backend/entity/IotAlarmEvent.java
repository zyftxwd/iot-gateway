package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_alarm_event")
public class IotAlarmEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long deviceId;
    private Long pointId;
    private Long alarmRuleId;
    private String protocolType;
    private String alarmType;
    private String severity;
    private String title;
    private String message;
    private String currentValue;
    private String thresholdText;
    private String status;
    private Long firstTime;
    private Long lastTime;
    private Long recoverTime;
    private Integer occurCount;
    private String sourceNode;
    private Long workOrderId;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private String deviceName;

    @TableField(exist = false)
    private String pointLabel;

    @TableField(exist = false)
    private String pointKey;

    @TableField(exist = false)
    private String unit;
}
