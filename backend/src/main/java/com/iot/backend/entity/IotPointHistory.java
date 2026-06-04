package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_point_history")
public class IotPointHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long deviceId;
    private Long pointId;
    private String pointKey;
    private String pointLabel;
    private String protocolType;
    private String valueText;
    private java.math.BigDecimal valueNumber;
    private String rawValue;
    private String quality;
    private Long collectTime;
    private Long collectCostMs;
    private String collectorNode;
}
