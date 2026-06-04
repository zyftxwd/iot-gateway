package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.backend.entity.IotAlarmRule;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.mapper.IotAlarmRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlarmRuleService {

    @Autowired
    private IotAlarmRuleMapper ruleMapper;

    @Autowired
    private IotCommPointService pointService;

    @Autowired
    private IotCommDeviceService deviceService;

    public List<IotAlarmRule> list(Long pointId, Long deviceId, Long projectId) {
        LambdaQueryWrapper<IotAlarmRule> wrapper = new LambdaQueryWrapper<IotAlarmRule>()
                .orderByDesc(IotAlarmRule::getId);
        if (pointId != null) {
            wrapper.eq(IotAlarmRule::getPointId, pointId);
        }
        if (deviceId != null) {
            wrapper.eq(IotAlarmRule::getDeviceId, deviceId);
        }
        if (projectId != null) {
            wrapper.eq(IotAlarmRule::getProjectId, projectId);
        }
        return ruleMapper.selectList(wrapper);
    }

    public List<IotAlarmRule> listEnabledByDevice(Long deviceId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<IotAlarmRule>()
                .eq(IotAlarmRule::getDeviceId, deviceId)
                .eq(IotAlarmRule::getEnabled, true));
    }

    public IotAlarmRule get(Long id) {
        return ruleMapper.selectById(id);
    }

    public IotAlarmRule create(IotAlarmRule rule) {
        normalize(rule);
        ruleMapper.insert(rule);
        return rule;
    }

    public IotAlarmRule update(Long id, IotAlarmRule rule) {
        rule.setId(id);
        normalize(rule);
        ruleMapper.updateById(rule);
        return ruleMapper.selectById(id);
    }

    public boolean delete(Long id) {
        return ruleMapper.deleteById(id) > 0;
    }

    private void normalize(IotAlarmRule rule) {
        IotCommPoint point = pointService.getPoint(rule.getPointId());
        if (point == null) {
            throw new IllegalArgumentException("point not found");
        }
        rule.setDeviceId(point.getCommDeviceId());
        if (rule.getProjectId() == null && deviceService.getDevice(point.getCommDeviceId()) != null) {
            rule.setProjectId(deviceService.getDevice(point.getCommDeviceId()).getProjectId());
        }
        if (rule.getRuleName() == null || rule.getRuleName().trim().length() == 0) {
            rule.setRuleName((point.getPointLabel() == null ? point.getPointKey() : point.getPointLabel()) + "报警");
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getSeverity() == null || rule.getSeverity().trim().length() == 0) {
            rule.setSeverity("WARN");
        } else {
            rule.setSeverity(rule.getSeverity().trim().toUpperCase());
        }
        if (rule.getConditionType() != null) {
            rule.setConditionType(rule.getConditionType().trim().toUpperCase());
        }
        if (rule.getImmediateAlarm() == null) {
            rule.setImmediateAlarm(false);
        }
        if (rule.getTriggerDurationMs() == null || rule.getTriggerDurationMs() < 0) {
            rule.setTriggerDurationMs(Boolean.TRUE.equals(rule.getImmediateAlarm()) ? 0 : 60000);
        }
        if (rule.getRecoverDurationMs() == null || rule.getRecoverDurationMs() < 0) {
            rule.setRecoverDurationMs(300000);
        }
        rule.setRecoverValue(null);
        rule.setRecoverHigh(null);
    }
}
