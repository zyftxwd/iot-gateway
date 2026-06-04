package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_operation_audit")
public class SysOperationAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private Long projectId;
    private Long deviceId;
    private Long pointId;
    private Long workOrderId;
    private String actionType;
    private String actionTarget;
    private String detail;
    private String result;
}
