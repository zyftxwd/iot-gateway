package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.dto.WorkOrderActionRequest;
import com.iot.backend.dto.WorkOrderCreateRequest;
import com.iot.backend.entity.IotAlarmEvent;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotMaintenanceCard;
import com.iot.backend.entity.IotProjectMember;
import com.iot.backend.entity.IotWorkOrder;
import com.iot.backend.entity.IotWorkOrderAttachment;
import com.iot.backend.entity.IotWorkOrderFlowLog;
import com.iot.backend.entity.IotWorkOrderParticipant;
import com.iot.backend.entity.IotWorkOrderPolicy;
import com.iot.backend.entity.SysUser;
import com.iot.backend.entity.SysUserProject;
import com.iot.backend.mapper.IotCommDeviceMapper;
import com.iot.backend.mapper.IotAlarmEventMapper;
import com.iot.backend.mapper.IotMaintenanceCardMapper;
import com.iot.backend.mapper.IotProjectMemberMapper;
import com.iot.backend.mapper.IotWorkOrderAttachmentMapper;
import com.iot.backend.mapper.IotWorkOrderFlowLogMapper;
import com.iot.backend.mapper.IotWorkOrderMapper;
import com.iot.backend.mapper.IotWorkOrderParticipantMapper;
import com.iot.backend.mapper.IotWorkOrderPolicyMapper;
import com.iot.backend.mapper.SysUserMapper;
import com.iot.backend.mapper.SysUserProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkOrderService {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_DISPATCHED = "DISPATCHED";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_CLOSED = "CLOSED";

    @Autowired
    private IotWorkOrderMapper workOrderMapper;

    @Autowired
    private IotCommDeviceMapper deviceMapper;

    @Autowired
    private IotAlarmEventMapper alarmEventMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private IotProjectMemberMapper projectMemberMapper;

    @Autowired
    private IotWorkOrderPolicyMapper policyMapper;

    @Autowired
    private IotWorkOrderParticipantMapper participantMapper;

    @Autowired
    private IotWorkOrderAttachmentMapper attachmentMapper;

    @Autowired
    private IotWorkOrderFlowLogMapper flowLogMapper;

    @Autowired
    private IotMaintenanceCardMapper maintenanceCardMapper;

    @Autowired
    private AlarmEventService alarmEventService;

    public List<IotWorkOrder> list(String status, Long projectId, Long deviceId, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<IotWorkOrder> wrapper = new LambdaQueryWrapper<IotWorkOrder>()
                .orderByDesc(IotWorkOrder::getCreateTime);
        if (!blank(status)) {
            wrapper.eq(IotWorkOrder::getStatus, status.trim().toUpperCase());
        }
        if (projectId != null) {
            wrapper.eq(IotWorkOrder::getProjectId, projectId);
        }
        if (deviceId != null) {
            wrapper.eq(IotWorkOrder::getDeviceId, deviceId);
        }
        if (visibleProjectIds != null) {
            wrapper.in(IotWorkOrder::getProjectId, visibleProjectIds);
        }
        wrapper.last("LIMIT 300");
        return workOrderMapper.selectList(wrapper);
    }

    public List<IotMaintenanceCard> listMaintenanceCards(Long projectId, Long deviceId, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<IotMaintenanceCard> wrapper = new LambdaQueryWrapper<IotMaintenanceCard>()
                .orderByDesc(IotMaintenanceCard::getCreateTime);
        if (projectId != null) {
            wrapper.eq(IotMaintenanceCard::getProjectId, projectId);
        }
        if (deviceId != null) {
            wrapper.eq(IotMaintenanceCard::getDeviceId, deviceId);
        }
        if (visibleProjectIds != null) {
            wrapper.in(IotMaintenanceCard::getProjectId, visibleProjectIds);
        }
        wrapper.last("LIMIT 300");
        return maintenanceCardMapper.selectList(wrapper);
    }

    public IotMaintenanceCard getMaintenanceCard(Long id) {
        return maintenanceCardMapper.selectById(id);
    }

    public List<IotWorkOrderAttachment> listAttachments(Long workOrderId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<IotWorkOrderAttachment>()
                .eq(IotWorkOrderAttachment::getWorkOrderId, workOrderId)
                .orderByDesc(IotWorkOrderAttachment::getUploadTime));
    }

    public IotWorkOrder get(Long id) {
        return workOrderMapper.selectById(id);
    }

    public Map<String, Object> detail(Long id) {
        IotWorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return null;
        }
        List<IotWorkOrderFlowLog> logs = flowLogMapper.selectList(new LambdaQueryWrapper<IotWorkOrderFlowLog>()
                .eq(IotWorkOrderFlowLog::getWorkOrderId, id)
                .orderByAsc(IotWorkOrderFlowLog::getActionTime));
        List<IotWorkOrderAttachment> attachments = listAttachments(id);
        IotMaintenanceCard card = maintenanceCardMapper.selectOne(new LambdaQueryWrapper<IotMaintenanceCard>()
                .eq(IotMaintenanceCard::getWorkOrderId, id)
                .last("LIMIT 1"));
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("logs", logs);
        result.put("attachments", attachments);
        result.put("maintenanceCard", card);
        return result;
    }

    @Transactional
    public boolean deleteWorkOrder(Long id) {
        IotWorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return false;
        }
        List<IotWorkOrderAttachment> attachments = listAttachments(id);
        attachmentMapper.delete(new LambdaQueryWrapper<IotWorkOrderAttachment>()
                .eq(IotWorkOrderAttachment::getWorkOrderId, id));
        flowLogMapper.delete(new LambdaQueryWrapper<IotWorkOrderFlowLog>()
                .eq(IotWorkOrderFlowLog::getWorkOrderId, id));
        participantMapper.delete(new LambdaQueryWrapper<IotWorkOrderParticipant>()
                .eq(IotWorkOrderParticipant::getWorkOrderId, id));
        maintenanceCardMapper.delete(new LambdaQueryWrapper<IotMaintenanceCard>()
                .eq(IotMaintenanceCard::getWorkOrderId, id));
        if (order.getAlarmEventId() != null) {
            IotAlarmEvent alarm = alarmEventMapper.selectById(order.getAlarmEventId());
            if (alarm != null && id.equals(alarm.getWorkOrderId())) {
                alarm.setWorkOrderId(null);
                alarmEventMapper.updateById(alarm);
            }
        }
        deleteAttachmentFiles(id, attachments);
        return workOrderMapper.deleteById(id) > 0;
    }

    @Transactional
    public boolean deleteMaintenanceCard(Long id) {
        return maintenanceCardMapper.deleteById(id) > 0;
    }

    private void deleteAttachmentFiles(Long workOrderId, List<IotWorkOrderAttachment> attachments) {
        if (workOrderId == null) {
            return;
        }
        for (IotWorkOrderAttachment attachment : attachments) {
            if (attachment.getFileUrl() == null) {
                continue;
            }
            String prefix = "/uploads/";
            if (!attachment.getFileUrl().startsWith(prefix)) {
                continue;
            }
            Path path = Paths.get("uploads", attachment.getFileUrl().substring(prefix.length()).replace("/", java.io.File.separator)).toAbsolutePath();
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
        Path dir = Paths.get("uploads", "work-orders", String.valueOf(workOrderId)).toAbsolutePath();
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    @Transactional
    public IotWorkOrder createManual(WorkOrderCreateRequest request, CurrentUserInfo operator) {
        Long projectId = resolveProjectId(request);
        requireDispatcher(operator, projectId);
        validatePlannedFinishTime(request.getPlannedFinishTime(), System.currentTimeMillis());

        IotWorkOrder order = baseOrder(operator);
        order.setSourceType("MANUAL");
        order.setProjectId(projectId);
        order.setDeviceId(request.getDeviceId());
        order.setPointId(request.getPointId());
        order.setTitle(cleanText(request.getTitle(), "现场工单"));
        order.setDescription(request.getDescription());
        order.setPriority(normalizePriority(request.getPriority()));
        order.setPlannedFinishTime(request.getPlannedFinishTime());
        order.setRemark(request.getRemark());
        workOrderMapper.insert(order);
        log(order, null, order.getStatus(), "CREATE", operator, "手工创建工单");

        if (request.getAssigneeUserId() != null || request.getVerifierUserId() != null) {
            WorkOrderActionRequest dispatch = new WorkOrderActionRequest();
            dispatch.setAction("DISPATCH");
            dispatch.setAssigneeUserId(request.getAssigneeUserId());
            dispatch.setVerifierUserId(request.getVerifierUserId());
            dispatch.setPlannedFinishTime(request.getPlannedFinishTime());
            dispatch.setRemark(request.getRemark());
            return applyAction(order.getId(), dispatch, operator);
        }
        return order;
    }

    @Transactional
    public IotWorkOrder createFromAlarm(IotAlarmEvent alarm, CurrentUserInfo operator) {
        requireDispatcher(operator, alarm.getProjectId());
        IotWorkOrder existing = workOrderMapper.selectOne(new LambdaQueryWrapper<IotWorkOrder>()
                .eq(IotWorkOrder::getAlarmEventId, alarm.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        IotWorkOrder order = baseOrder(operator);
        order.setSourceType("ALARM");
        order.setAlarmEventId(alarm.getId());
        order.setProjectId(alarm.getProjectId());
        order.setDeviceId(alarm.getDeviceId());
        order.setPointId(alarm.getPointId());
        order.setTitle(cleanText(alarm.getTitle(), "报警工单"));
        order.setDescription(alarm.getMessage());
        order.setPriority(priorityFromSeverity(alarm.getSeverity()));
        workOrderMapper.insert(order);
        alarmEventService.linkWorkOrder(alarm.getId(), order.getId());
        log(order, null, order.getStatus(), "CREATE_FROM_ALARM", operator, "报警转工单");
        return order;
    }

    @Transactional
    public IotWorkOrder applyAction(Long id, WorkOrderActionRequest request, CurrentUserInfo operator) {
        IotWorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return null;
        }
        IotWorkOrderPolicy policy = policy(order.getProjectId());
        String fromStatus = order.getStatus();
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase();
        long now = System.currentTimeMillis();

        if ("DISPATCH".equals(action)) {
            requireDispatcher(operator, order.getProjectId());
            assertStatus(order, STATUS_CREATED, STATUS_DISPATCHED);
            dispatch(order, request, operator, policy, now);
        } else if ("ACCEPT".equals(action)) {
            assertStatus(order, STATUS_DISPATCHED);
            requireAssignee(order, operator);
            order.setStatus(STATUS_ACCEPTED);
            order.setAcceptTime(now);
        } else if ("START".equals(action)) {
            assertStatus(order, STATUS_ACCEPTED);
            requireAssignee(order, operator);
            order.setStatus(STATUS_PROCESSING);
            order.setProcessTime(now);
        } else if ("FINISH".equals(action)) {
            assertStatus(order, STATUS_PROCESSING);
            requireAssignee(order, operator);
            fillProcessResult(order, request, policy);
            order.setStatus(STATUS_FINISHED);
            order.setFinishTime(now);
        } else if ("REJECT".equals(action)) {
            assertStatus(order, STATUS_FINISHED);
            requireVerifier(order, operator, policy);
            order.setStatus(STATUS_PROCESSING);
            order.setRemark(cleanText(request.getRemark(), "验收驳回"));
        } else if ("VERIFY".equals(action)) {
            assertStatus(order, STATUS_FINISHED);
            requireVerifier(order, operator, policy);
            order.setVerifyTime(now);
            if (Boolean.TRUE.equals(policy.getAutoCloseAfterVerify())) {
                order.setStatus(STATUS_CLOSED);
                order.setCloseTime(now);
            } else {
                order.setStatus(STATUS_VERIFIED);
            }
        } else if ("CLOSE".equals(action)) {
            assertStatus(order, STATUS_VERIFIED);
            requireCloser(order, operator, policy);
            order.setStatus(STATUS_CLOSED);
            order.setCloseTime(now);
        } else {
            throw new IllegalArgumentException("不支持的工单操作");
        }

        if (!blank(request.getRemark()) && !"REJECT".equals(action)) {
            order.setRemark(request.getRemark().trim());
        }
        workOrderMapper.updateById(order);
        log(order, fromStatus, order.getStatus(), action, operator, request.getRemark());

        if ("VERIFY".equals(action) && STATUS_CLOSED.equals(order.getStatus())) {
            log(order, STATUS_VERIFIED, STATUS_CLOSED, "AUTO_CLOSE", operator, "验收通过后自动关闭");
        }
        if (STATUS_CLOSED.equals(order.getStatus()) && Boolean.TRUE.equals(policy.getAutoArchiveAfterClose())) {
            IotMaintenanceCard card = archive(order, operator);
            if (order.getArchiveCardId() == null) {
                order.setArchiveCardId(card.getId());
                workOrderMapper.updateById(order);
                log(order, STATUS_CLOSED, STATUS_CLOSED, "AUTO_ARCHIVE", operator, "自动生成维修资料卡");
            }
        }
        return workOrderMapper.selectById(id);
    }

    private void dispatch(IotWorkOrder order, WorkOrderActionRequest request, CurrentUserInfo operator,
                          IotWorkOrderPolicy policy, long now) {
        if (request.getAssigneeUserId() == null) {
            throw new IllegalArgumentException("派单必须指定处理人");
        }
        if (request.getVerifierUserId() == null) {
            throw new IllegalArgumentException("派单必须指定验收人");
        }
        if (!Boolean.TRUE.equals(policy.getAllowDispatcherAsAssignee())
                && operator != null && operator.getUserId().equals(request.getAssigneeUserId())) {
            throw new IllegalArgumentException("当前项目默认不允许派单人同时作为处理人");
        }
        if (!Boolean.TRUE.equals(policy.getAllowDispatcherAsVerifier())
                && operator != null && operator.getUserId().equals(request.getVerifierUserId())) {
            throw new IllegalArgumentException("当前项目不允许派单人同时作为验收人");
        }
        if (!Boolean.TRUE.equals(policy.getAllowAssigneeVerifySelf())
                && request.getAssigneeUserId().equals(request.getVerifierUserId())) {
            throw new IllegalArgumentException("当前项目默认不允许处理人验收自己的工单");
        }
        validatePlannedFinishTime(request.getPlannedFinishTime(), now);

        SysUser assignee = userMapper.selectById(request.getAssigneeUserId());
        SysUser verifier = userMapper.selectById(request.getVerifierUserId());
        if (assignee == null || verifier == null) {
            throw new IllegalArgumentException("处理人或验收人不存在");
        }
        if (!hasRole(userToCurrent(assignee), order.getProjectId(), "MAINTAINER", "PROJECT_MANAGER")
                && !hasProjectPermission(assignee.getUserId(), order.getProjectId(), "OPERATE", "ADMIN")) {
            throw new IllegalArgumentException("处理人没有该项目的处理权限");
        }
        if (!hasRole(userToCurrent(verifier), order.getProjectId(), "VERIFIER", "PROJECT_MANAGER")
                && !hasProjectPermission(verifier.getUserId(), order.getProjectId(), "ADMIN")) {
            throw new IllegalArgumentException("验收人没有该项目的验收权限");
        }

        order.setAssigneeUserId(assignee.getUserId());
        order.setAssigneeName(displayName(assignee));
        order.setVerifierUserId(verifier.getUserId());
        order.setVerifierName(displayName(verifier));
        order.setPlannedFinishTime(request.getPlannedFinishTime());
        order.setStatus(STATUS_DISPATCHED);
        order.setDispatchTime(now);
        saveParticipant(order, userMapper.selectById(operator.getUserId()), "DISPATCHER", now);
        saveParticipant(order, assignee, "ASSIGNEE", now);
        saveParticipant(order, verifier, "VERIFIER", now);
    }

    private void fillProcessResult(IotWorkOrder order, WorkOrderActionRequest request, IotWorkOrderPolicy policy) {
        if (Boolean.TRUE.equals(policy.getRequireFaultReason()) && blank(request.getFaultReason())) {
            throw new IllegalArgumentException("提交完成必须填写故障原因");
        }
        if (Boolean.TRUE.equals(policy.getRequireProcessMeasure()) && blank(request.getProcessMeasure())) {
            throw new IllegalArgumentException("提交完成必须填写处理措施");
        }
        order.setFaultType(trim(request.getFaultType()));
        order.setFaultReason(trim(request.getFaultReason()));
        order.setProcessMeasure(trim(request.getProcessMeasure()));
        order.setProcessResult(trim(request.getProcessResult()));
        if (Boolean.TRUE.equals(policy.getRequireProcessPhoto())
                && attachmentMapper.selectCount(new LambdaQueryWrapper<IotWorkOrderAttachment>()
                .eq(IotWorkOrderAttachment::getWorkOrderId, order.getId())
                .eq(IotWorkOrderAttachment::getAttachmentType, "PROCESS_PHOTO")) == 0) {
            throw new IllegalArgumentException("当前项目要求上传处理照片");
        }
    }

    public Map<String, List<SysUser>> candidates(Long projectId, CurrentUserInfo operator) {
        requireDispatcher(operator, projectId);
        List<SysUser> allUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getUserId));
        List<SysUser> assignees = new ArrayList<>();
        List<SysUser> verifiers = new ArrayList<>();
        for (SysUser user : allUsers) {
            if (canBeAssignee(user, projectId)) {
                user.setPassword(null);
                assignees.add(user);
            }
            if (canBeVerifier(user, projectId)) {
                user.setPassword(null);
                verifiers.add(user);
            }
        }
        Map<String, List<SysUser>> result = new HashMap<>();
        result.put("assignees", assignees);
        result.put("verifiers", verifiers);
        return result;
    }

    public IotWorkOrderPolicy getPolicy(Long projectId, CurrentUserInfo operator) {
        requireDispatcher(operator, projectId);
        return policy(projectId);
    }

    @Transactional
    public IotWorkOrderPolicy savePolicy(Long projectId, IotWorkOrderPolicy request, CurrentUserInfo operator) {
        if (!hasRole(operator, projectId, "PROJECT_MANAGER")) {
            throw new IllegalArgumentException("只有项目管理员可以维护工单流程设置");
        }
        IotWorkOrderPolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<IotWorkOrderPolicy>()
                .eq(IotWorkOrderPolicy::getProjectId, projectId)
                .last("LIMIT 1"));
        if (policy == null) {
            policy = new IotWorkOrderPolicy();
            policy.setProjectId(projectId);
        }
        policy.setAutoCreateFromAlarm(nvl(request.getAutoCreateFromAlarm(), false));
        policy.setAllowDispatcherAsAssignee(nvl(request.getAllowDispatcherAsAssignee(), false));
        policy.setAllowDispatcherAsVerifier(nvl(request.getAllowDispatcherAsVerifier(), true));
        policy.setAllowAssigneeVerifySelf(nvl(request.getAllowAssigneeVerifySelf(), false));
        policy.setAutoCloseAfterVerify(nvl(request.getAutoCloseAfterVerify(), true));
        policy.setAutoArchiveAfterClose(nvl(request.getAutoArchiveAfterClose(), true));
        policy.setRequireProcessPhoto(nvl(request.getRequireProcessPhoto(), false));
        policy.setRequireFaultReason(nvl(request.getRequireFaultReason(), true));
        policy.setRequireProcessMeasure(nvl(request.getRequireProcessMeasure(), true));
        policy.setAcceptTimeoutMinutes(request.getAcceptTimeoutMinutes());
        policy.setFinishTimeoutMinutes(request.getFinishTimeoutMinutes());
        if (policy.getId() == null) {
            policyMapper.insert(policy);
        } else {
            policyMapper.updateById(policy);
        }
        return policy;
    }

    @Transactional
    public IotWorkOrderAttachment uploadAttachment(Long workOrderId, MultipartFile file, CurrentUserInfo operator) throws IOException {
        IotWorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (!canTouchOrder(order, operator)) {
            throw new IllegalArgumentException("当前账号不能上传该工单附件");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        String safeName = original.replaceAll("[\\\\/:*?\"<>|]", "_");
        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + safeName;
        Path dir = Paths.get("uploads", "work-orders", String.valueOf(workOrderId)).toAbsolutePath();
        Files.createDirectories(dir);
        Path target = dir.resolve(storedName);
        file.transferTo(target.toFile());

        IotWorkOrderAttachment attachment = new IotWorkOrderAttachment();
        attachment.setWorkOrderId(workOrderId);
        attachment.setAttachmentType("PROCESS_PHOTO");
        attachment.setFileName(safeName);
        attachment.setFileUrl("/uploads/work-orders/" + workOrderId + "/" + storedName);
        attachment.setFileSize(file.getSize());
        if (operator != null) {
            attachment.setUploaderUserId(operator.getUserId());
            attachment.setUploaderName(operator.getNickName() == null ? operator.getUsername() : operator.getNickName());
        }
        attachment.setUploadTime(System.currentTimeMillis());
        attachmentMapper.insert(attachment);
        return attachment;
    }

    private IotMaintenanceCard archive(IotWorkOrder order, CurrentUserInfo operator) {
        IotMaintenanceCard existing = maintenanceCardMapper.selectOne(new LambdaQueryWrapper<IotMaintenanceCard>()
                .eq(IotMaintenanceCard::getWorkOrderId, order.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        IotMaintenanceCard card = new IotMaintenanceCard();
        card.setWorkOrderId(order.getId());
        card.setAlarmEventId(order.getAlarmEventId());
        card.setProjectId(order.getProjectId());
        card.setDeviceId(order.getDeviceId());
        card.setPointId(order.getPointId());
        card.setTitle(order.getTitle());
        card.setFaultType(order.getFaultType());
        card.setFaultReason(order.getFaultReason());
        card.setProcessMeasure(order.getProcessMeasure());
        card.setProcessResult(order.getProcessResult());
        card.setKeywords(buildKeywords(order));
        card.setTags(cleanText(order.getSourceType(), "WORK_ORDER") + "," + cleanText(order.getPriority(), "MINOR"));
        if (operator != null) {
            card.setCreatorUserId(operator.getUserId());
            card.setCreatorName(operator.getNickName() == null ? operator.getUsername() : operator.getNickName());
        }
        card.setCreateTime(now);
        maintenanceCardMapper.insert(card);
        return card;
    }

    private IotWorkOrder baseOrder(CurrentUserInfo operator) {
        long now = System.currentTimeMillis();
        IotWorkOrder order = new IotWorkOrder();
        order.setOrderNo(nextOrderNo(now));
        order.setStatus(STATUS_CREATED);
        order.setPriority("MINOR");
        order.setFlowKey("STANDARD_MAINTENANCE");
        order.setCreateTime(now);
        if (operator != null) {
            order.setCreatorUserId(operator.getUserId());
            order.setCreatorName(operator.getNickName() == null ? operator.getUsername() : operator.getNickName());
            order.setDeptId(operator.getDeptId());
        }
        return order;
    }

    private IotWorkOrderPolicy policy(Long projectId) {
        IotWorkOrderPolicy policy = projectId == null ? null : policyMapper.selectOne(new LambdaQueryWrapper<IotWorkOrderPolicy>()
                .eq(IotWorkOrderPolicy::getProjectId, projectId)
                .last("LIMIT 1"));
        if (policy == null) {
            policy = new IotWorkOrderPolicy();
            policy.setProjectId(projectId);
            policy.setAutoCreateFromAlarm(false);
            policy.setAllowDispatcherAsAssignee(false);
            policy.setAllowDispatcherAsVerifier(true);
            policy.setAllowAssigneeVerifySelf(false);
            policy.setAutoCloseAfterVerify(true);
            policy.setAutoArchiveAfterClose(true);
            policy.setRequireProcessPhoto(false);
            policy.setRequireFaultReason(true);
            policy.setRequireProcessMeasure(true);
        }
        return policy;
    }

    private Boolean nvl(Boolean value, Boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private void requireDispatcher(CurrentUserInfo user, Long projectId) {
        if (!hasRole(user, projectId, "PROJECT_MANAGER", "DISPATCHER")) {
            throw new IllegalArgumentException("当前账号没有派单权限");
        }
    }

    private void requireAssignee(IotWorkOrder order, CurrentUserInfo user) {
        if (user == null || order.getAssigneeUserId() == null || !order.getAssigneeUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("只有当前工单处理人可以执行该操作");
        }
    }

    private void requireVerifier(IotWorkOrder order, CurrentUserInfo user, IotWorkOrderPolicy policy) {
        if (hasRole(user, order.getProjectId(), "PROJECT_MANAGER")) {
            return;
        }
        if (user == null || order.getVerifierUserId() == null || !order.getVerifierUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("只有当前工单验收人或项目管理员可以验收");
        }
        if (!Boolean.TRUE.equals(policy.getAllowAssigneeVerifySelf()) && user.getUserId().equals(order.getAssigneeUserId())) {
            throw new IllegalArgumentException("当前项目默认不允许处理人验收自己的工单");
        }
    }

    private void requireCloser(IotWorkOrder order, CurrentUserInfo user, IotWorkOrderPolicy policy) {
        if (hasRole(user, order.getProjectId(), "PROJECT_MANAGER")) {
            return;
        }
        if (user == null || order.getVerifierUserId() == null || !order.getVerifierUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("只有验收人或项目管理员可以关闭工单");
        }
        if (!Boolean.TRUE.equals(policy.getAllowAssigneeVerifySelf()) && user.getUserId().equals(order.getAssigneeUserId())) {
            throw new IllegalArgumentException("当前项目默认不允许处理人关闭自己的工单");
        }
    }

    private boolean hasRole(CurrentUserInfo user, Long projectId, String... roles) {
        if (user == null) {
            return false;
        }
        if ("admin".equalsIgnoreCase(user.getRoleKey())) {
            return true;
        }
        if (projectId == null) {
            return false;
        }
        List<IotProjectMember> members = projectMemberMapper.selectList(new LambdaQueryWrapper<IotProjectMember>()
                .eq(IotProjectMember::getProjectId, projectId)
                .eq(IotProjectMember::getUserId, user.getUserId())
                .eq(IotProjectMember::getStatus, "ACTIVE"));
        for (IotProjectMember member : members) {
            for (String role : roles) {
                if (role.equalsIgnoreCase(member.getRoleKey())) {
                    return true;
                }
            }
        }

        if (hasProjectPermission(user.getUserId(), projectId, "ADMIN")) {
            for (String role : roles) {
                if ("PROJECT_MANAGER".equals(role) || "DISPATCHER".equals(role) || "VERIFIER".equals(role)) {
                    return true;
                }
            }
        }
        if (hasProjectPermission(user.getUserId(), projectId, "OPERATE", "ADMIN")) {
            for (String role : roles) {
                if ("MAINTAINER".equals(role)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canBeAssignee(SysUser user, Long projectId) {
        return hasRole(userToCurrent(user), projectId, "MAINTAINER", "PROJECT_MANAGER")
                || hasProjectPermission(user.getUserId(), projectId, "OPERATE", "ADMIN");
    }

    private boolean canBeVerifier(SysUser user, Long projectId) {
        return hasRole(userToCurrent(user), projectId, "VERIFIER", "PROJECT_MANAGER")
                || hasProjectPermission(user.getUserId(), projectId, "ADMIN");
    }

    private boolean canTouchOrder(IotWorkOrder order, CurrentUserInfo operator) {
        if (operator == null) {
            return false;
        }
        return operator.getUserId().equals(order.getAssigneeUserId())
                || operator.getUserId().equals(order.getVerifierUserId())
                || hasRole(operator, order.getProjectId(), "PROJECT_MANAGER", "DISPATCHER");
    }

    private boolean hasProjectPermission(Long userId, Long projectId, String... levels) {
        if (userId == null || projectId == null) {
            return false;
        }
        SysUserProject permission = userProjectMapper.selectOne(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getProjectId, projectId)
                .eq(SysUserProject::getUserId, userId)
                .last("LIMIT 1"));
        if (permission == null) {
            return false;
        }
        for (String level : levels) {
            if (level.equalsIgnoreCase(permission.getPermissionLevel())) {
                return true;
            }
        }
        return false;
    }

    private CurrentUserInfo userToCurrent(SysUser user) {
        CurrentUserInfo info = new CurrentUserInfo();
        info.setUserId(user.getUserId());
        info.setUsername(user.getUsername());
        info.setNickName(user.getNickName());
        info.setDeptId(user.getDeptId());
        info.setRoleKey(user.getRoleKey());
        return info;
    }

    private void saveParticipant(IotWorkOrder order, SysUser user, String role, long now) {
        if (user == null) {
            return;
        }
        IotWorkOrderParticipant existing = participantMapper.selectOne(new LambdaQueryWrapper<IotWorkOrderParticipant>()
                .eq(IotWorkOrderParticipant::getWorkOrderId, order.getId())
                .eq(IotWorkOrderParticipant::getUserId, user.getUserId())
                .eq(IotWorkOrderParticipant::getParticipantRole, role)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        IotWorkOrderParticipant participant = new IotWorkOrderParticipant();
        participant.setWorkOrderId(order.getId());
        participant.setUserId(user.getUserId());
        participant.setUsername(displayName(user));
        participant.setParticipantRole(role);
        participant.setCreateTime(now);
        participantMapper.insert(participant);
    }

    private void log(IotWorkOrder order, String fromStatus, String toStatus, String action, CurrentUserInfo operator, String remark) {
        IotWorkOrderFlowLog log = new IotWorkOrderFlowLog();
        log.setWorkOrderId(order.getId());
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setAction(action);
        if (operator != null) {
            log.setOperatorUserId(operator.getUserId());
            log.setOperatorName(operator.getNickName() == null ? operator.getUsername() : operator.getNickName());
        }
        log.setRemark(remark);
        log.setActionTime(System.currentTimeMillis());
        flowLogMapper.insert(log);
    }

    private void assertStatus(IotWorkOrder order, String... allowed) {
        for (String status : allowed) {
            if (status.equalsIgnoreCase(order.getStatus())) {
                return;
            }
        }
        throw new IllegalArgumentException("当前工单状态不允许执行该操作");
    }

    private String nextOrderNo(long now) {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "WO" + day + now;
    }

    private String cleanText(String value, String defaultValue) {
        return blank(value) ? defaultValue : value.trim();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizePriority(String priority) {
        return blank(priority) ? "MINOR" : priority.trim().toUpperCase();
    }

    private String priorityFromSeverity(String severity) {
        String value = severity == null ? "" : severity.trim().toUpperCase();
        if ("INFO".equals(value)) return "INFO";
        if ("WARN".equals(value) || "WARNING".equals(value)) return "WARN";
        if ("MINOR".equals(value)) return "MINOR";
        if ("MAJOR".equals(value)) return "MAJOR";
        if ("CRITICAL".equals(value)) return "CRITICAL";
        return "MINOR";
    }

    private String displayName(SysUser user) {
        return user.getNickName() == null || user.getNickName().trim().length() == 0 ? user.getUsername() : user.getNickName();
    }

    private void validatePlannedFinishTime(Long plannedFinishTime, long now) {
        if (plannedFinishTime != null && plannedFinishTime < now) {
            throw new IllegalArgumentException("计划完成时间不能早于当前时间");
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private String buildKeywords(IotWorkOrder order) {
        StringBuilder builder = new StringBuilder();
        append(builder, order.getTitle());
        append(builder, order.getFaultType());
        append(builder, order.getFaultReason());
        append(builder, order.getProcessMeasure());
        append(builder, order.getProcessResult());
        return builder.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (blank(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value.trim());
    }

    public Long resolveProjectId(WorkOrderCreateRequest request) {
        if (request.getProjectId() != null) {
            return request.getProjectId();
        }
        if (request.getDeviceId() == null) {
            return null;
        }
        IotCommDevice device = deviceMapper.selectById(request.getDeviceId());
        return device == null ? null : device.getProjectId();
    }
}
