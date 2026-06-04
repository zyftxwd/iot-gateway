package com.iot.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.backend.entity.IotAlarmEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IotAlarmEventMapper extends BaseMapper<IotAlarmEvent> {
}
