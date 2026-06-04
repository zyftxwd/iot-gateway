package com.iot.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.backend.entity.IotPointHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IotPointHistoryMapper extends BaseMapper<IotPointHistory> {
}
