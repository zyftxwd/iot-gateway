package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_work_order_attachment")
public class IotWorkOrderAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private String attachmentType;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private Long uploaderUserId;
    private String uploaderName;
    private Long uploadTime;
}
