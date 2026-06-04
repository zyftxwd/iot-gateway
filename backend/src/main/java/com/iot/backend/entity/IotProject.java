package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_project")
public class IotProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectName;
    private String projectCode;
    private String ownerName;
    private String status;
    private String remark;
}
