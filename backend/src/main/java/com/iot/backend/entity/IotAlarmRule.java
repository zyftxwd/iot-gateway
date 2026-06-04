package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("iot_alarm_rule")
public class IotAlarmRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long deviceId;
    private Long pointId;
    private String ruleName;
    private Boolean enabled;
    private String severity;
    private String conditionType;
    private BigDecimal thresholdValue;
    private BigDecimal thresholdHigh;
    private BigDecimal recoverValue;
    private BigDecimal recoverHigh;
    private Boolean immediateAlarm;
    private Integer triggerDurationMs;
    private Integer recoverDurationMs;
    private String remark;
}
