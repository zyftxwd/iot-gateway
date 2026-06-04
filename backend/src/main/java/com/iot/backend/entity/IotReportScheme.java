package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_report_scheme")
public class IotReportScheme {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String reportType;
    private String schemeName;
    private String filtersJson;
    private String layoutJson;
    private Integer sortNo;
    private Long createTime;
    private Long updateTime;
}
