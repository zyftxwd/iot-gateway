package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_comm_device")
public class IotCommDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long groupId;
    private String deviceName;
    private String deviceType;
    private String protocolType;
    private String ipAddress;
    private Integer port;
    private String status;
    private Integer collectIntervalMs;
    private Boolean historyEnabled;
    private String historyMode;
    private Integer historyIntervalMs;
    private java.math.BigDecimal changeThreshold;
    private Boolean storeOnChange;
    private Long lastCollectTime;
    private Long lastSuccessTime;
    private String lastErrorMessage;
    private Integer failCount;
    private String extConfig;
    private String remark;

    @TableField(exist = false)
    private Integer activePointAlarmCount;

    @TableField(exist = false)
    private String activePointAlarmSummary;
}
