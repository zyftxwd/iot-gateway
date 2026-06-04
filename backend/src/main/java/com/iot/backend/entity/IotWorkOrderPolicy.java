package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_work_order_policy")
public class IotWorkOrderPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Boolean autoCreateFromAlarm;
    private Boolean allowDispatcherAsAssignee;
    private Boolean allowDispatcherAsVerifier;
    private Boolean allowAssigneeVerifySelf;
    private Boolean autoCloseAfterVerify;
    private Boolean autoArchiveAfterClose;
    private Boolean requireProcessPhoto;
    private Boolean requireFaultReason;
    private Boolean requireProcessMeasure;
    private Integer acceptTimeoutMinutes;
    private Integer finishTimeoutMinutes;
}
