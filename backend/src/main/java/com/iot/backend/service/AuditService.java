package com.iot.backend.service;

import com.alibaba.fastjson2.JSON;
import com.iot.backend.entity.SysOperationAudit;
import com.iot.backend.mapper.SysOperationAuditMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    @Autowired
    private SysOperationAuditMapper auditMapper;

    public void record(Long userId, String username, Long projectId, Long deviceId, Long pointId,
                       Long workOrderId, String actionType, String actionTarget, Object detail, String result) {
        SysOperationAudit audit = new SysOperationAudit();
        audit.setUserId(userId);
        audit.setUsername(username);
        audit.setProjectId(projectId);
        audit.setDeviceId(deviceId);
        audit.setPointId(pointId);
        audit.setWorkOrderId(workOrderId);
        audit.setActionType(actionType);
        audit.setActionTarget(actionTarget);
        audit.setDetail(detail == null ? null : JSON.toJSONString(detail));
        audit.setResult(result == null ? "SUCCESS" : result);
        auditMapper.insert(audit);
    }
}
