package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_work_order_flow_log")
public class IotWorkOrderFlowLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private String fromStatus;
    private String toStatus;
    private String action;
    private Long operatorUserId;
    private String operatorName;
    private String remark;
    private Long actionTime;
}
