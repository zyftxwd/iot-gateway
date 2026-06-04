package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_work_order_participant")
public class IotWorkOrderParticipant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private Long userId;
    private String username;
    private String participantRole;
    private Long createTime;
}
