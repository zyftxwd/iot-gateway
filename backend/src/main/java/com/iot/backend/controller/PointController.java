package com.iot.backend.controller;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.iot.backend.common.Result;
import com.iot.backend.dto.PointImportConfirmRequest;
import com.iot.backend.dto.PointImportPreviewResult;
import com.iot.backend.dto.PointImportResult;
import com.iot.backend.dto.PointRuntimeValue;
import com.iot.backend.dto.PointWriteRequest;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.protocol.ProtocolBrowseNode;
import com.iot.backend.service.IotCommDeviceService;
import com.iot.backend.service.IotCommPointService;
import com.iot.backend.service.AuditService;
import com.iot.backend.service.PermissionService;
import com.iot.backend.service.PointHistoryService;
import com.iot.backend.entity.IotPointHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/points")
public class PointController {

    @Autowired
    private IotCommPointService pointService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PointHistoryService pointHistoryService;

    @Autowired
    private IotCommDeviceService deviceService;

    @GetMapping
    public Result<List<IotCommPoint>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestParam(required = false) Long deviceId,
                                           @RequestParam(required = false) String keyword) {
        if (deviceId != null && !permissionService.canViewDevice(authorization, deviceId)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        return Result.success(pointService.listPoints(deviceId, keyword));
    }

    @GetMapping("/{id}")
    public Result<IotCommPoint> get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @PathVariable Long id) {
        if (!permissionService.canViewPoint(authorization, id)) {
            return Result.error(403, "current account has no permission to view this point");
        }
        IotCommPoint point = pointService.getPoint(id);
        if (point == null) {
            return Result.error(404, "point not found");
        }
        return Result.success(point);
    }

    @PostMapping
    public Result<IotCommPoint> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody IotCommPoint point) {
        if (!permissionService.canManageDevice(authorization, point.getCommDeviceId())) {
            return Result.error(403, "当前账号没有该设备的点表管理权限");
        }
        return Result.success(pointService.createPoint(point));
    }

    @PostMapping("/batch")
    public Result<List<IotCommPoint>> createBatch(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @RequestParam(required = false) Long deviceId,
                                                  @RequestBody List<IotCommPoint> points) {
        if (!permissionService.canManageDevice(authorization, deviceId)) {
            return Result.error(403, "当前账号没有该设备的点表管理权限");
        }
        return Result.success(pointService.createPoints(deviceId, points));
    }

    @PostMapping("/discover")
    public Result<List<IotCommPoint>> discover(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @RequestParam Long deviceId) {
        if (!permissionService.canManageDevice(authorization, deviceId)) {
            return Result.error(403, "当前账号没有该设备的点表管理权限");
        }
        return Result.success(pointService.discoverPoints(deviceId));
    }

    @PostMapping("/discover/preview")
    public Result<PointImportPreviewResult> previewDiscover(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @RequestParam Long deviceId) {
        if (!permissionService.canManageDevice(authorization, deviceId)) {
            return Result.error(403, "当前账号没有该设备的点表管理权限");
        }
        return Result.success(pointService.previewDiscoverPoints(deviceId));
    }

    @PostMapping("/browse")
    public Result<List<ProtocolBrowseNode>> browse(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @RequestParam Long deviceId) {
        if (!permissionService.canManageDevice(authorization, deviceId)) {
            return Result.error(403, "当前账号没有该设备的点表管理权限");
        }
        return Result.success(pointService.browseDeviceNodes(deviceId));
    }

    @GetMapping("/template")
    public void downloadTemplate(@RequestParam(required = false) Long deviceId,
                                 @RequestParam(required = false) String protocolType,
                                 HttpServletResponse response) throws Exception {
        String protocol = resolveTemplateProtocol(deviceId, protocolType);
        String fileName = URLEncoder.encode(protocol + "_点表导入模板.xlsx", StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

        ExcelWriter writer = ExcelUtil.getWriter(true);
        if ("MQTT".equalsIgnoreCase(protocol)) {
            writeMqttTemplate(writer);
        } else if ("OPC_UA".equalsIgnoreCase(protocol)) {
            writeOpcUaTemplate(writer);
        } else {
            writeModbusTemplate(writer);
        }

        ServletOutputStream out = response.getOutputStream();
        writer.flush(out, true);
        writer.close();
        out.close();
    }

    private String resolveTemplateProtocol(Long deviceId, String protocolType) {
        if (deviceId != null) {
            IotCommDevice device = deviceService.getDevice(deviceId);
            if (device != null && device.getProtocolType() != null) {
                return device.getProtocolType().trim().toUpperCase();
            }
        }
        if (protocolType != null && protocolType.trim().length() > 0) {
            return protocolType.trim().toUpperCase();
        }
        return "MODBUS_TCP";
    }

    private void writeModbusTemplate(ExcelWriter writer) {
        writer.renameSheet("点表模板");
        writer.writeHeadRow(Arrays.asList(
                "点位名称", "数据标识", "地址", "功能码", "从站", "长度",
                "数据类型", "字节序", "字序", "倍率", "小数位数", "单位", "读写权限", "启用", "点位描述"
        ));
        writer.writeRow(Arrays.asList(
                "出口温度", "outlet_temperature", "40001", 3, 1, 1,
                "UInt16", "ABCD", "AB", 0.1, 2, "℃", "只读", "是", "示例：工程量=原始值*倍率"
        ));
        writer.writeRow(Arrays.asList(
                "风机启停", "fan_start_stop", "00001", 1, 1, 1,
                "Boolean", "ABCD", "AB", 1, 0, "", "读写", "是", "示例：开关量可写0/1"
        ));

        writer.setSheet("填写说明");
        writer.writeHeadRow(Arrays.asList("字段", "是否必填", "说明"));
        writer.writeRow(Arrays.asList("点位名称", "是", "给人看的名称，例如出口温度"));
        writer.writeRow(Arrays.asList("数据标识", "否", "系统字段名，例如 outlet_temperature；不填时按地址生成"));
        writer.writeRow(Arrays.asList("地址", "是", "Modbus 地址，例如 40001 或 00001"));
        writer.writeRow(Arrays.asList("功能码", "否", "1/2/3/4/5/6/15/16，默认按数据类型推断"));
        writer.writeRow(Arrays.asList("数据类型", "是", "Boolean、Int16、UInt16、Int32、UInt32、Float32"));
        writer.writeRow(Arrays.asList("小数位数", "否", "数值显示保留几位小数，默认 2，建议 0-6"));
        writer.writeRow(Arrays.asList("读写权限", "否", "只读或读写，默认只读"));
        writer.setSheet("点表模板");
    }

    private void writeMqttTemplate(ExcelWriter writer) {
        writer.renameSheet("点表模板");
        writer.writeHeadRow(Arrays.asList(
                "点位名称", "数据标识", "JSON字段路径", "数据类型", "倍率",
                "小数位数", "单位", "读写权限", "启用", "点位描述"
        ));
        writer.writeRow(Arrays.asList(
                "出口温度", "outlet_temperature", "temperature", "Float32", 1,
                2, "℃", "只读", "是", "JSON 示例：{\"temperature\": 26.5}"
        ));
        writer.writeRow(Arrays.asList(
                "电机转速", "motor_speed", "motor.speed", "Float32", 1,
                0, "rpm", "只读", "是", "嵌套 JSON 示例：{\"motor\":{\"speed\": 1450}}"
        ));
        writer.writeRow(Arrays.asList(
                "设备状态", "status_text", "status", "String", 1,
                0, "", "只读", "是", "字符串字段会按原文保存"
        ));

        writer.setSheet("填写说明");
        writer.writeHeadRow(Arrays.asList("字段", "是否必填", "说明"));
        writer.writeRow(Arrays.asList("点位名称", "是", "给人看的名称，例如出口温度"));
        writer.writeRow(Arrays.asList("数据标识", "否", "系统字段名，例如 outlet_temperature；不填时按字段路径生成"));
        writer.writeRow(Arrays.asList("JSON字段路径", "是", "MQTT Payload 中的字段路径，例如 temperature、motor.speed、values[0]"));
        writer.writeRow(Arrays.asList("数据类型", "是", "Boolean、Int16、UInt16、Int32、UInt32、Float32、String"));
        writer.writeRow(Arrays.asList("倍率", "否", "数值换算倍率，默认 1；MQTT 字段通常保持 1"));
        writer.writeRow(Arrays.asList("小数位数", "否", "数值显示保留几位小数，默认 2，建议 0-6"));
        writer.writeRow(Arrays.asList("读写权限", "否", "只读或读写；MQTT 写入需要设备配置 publishTopic"));
        writer.writeRow(Arrays.asList("建议", "否", "未知 JSON 结构时优先使用页面上的“发现点位”，不要直接套模板"));
        writer.setSheet("点表模板");
    }

    private void writeOpcUaTemplate(ExcelWriter writer) {
        writer.renameSheet("点表模板");
        writer.writeHeadRow(Arrays.asList(
                "点位名称", "数据标识", "NodeId", "数据类型", "倍率",
                "小数位数", "单位", "读写权限", "启用", "点位描述"
        ));
        writer.writeRow(Arrays.asList(
                "出口温度", "opc_temperature", "ns=2;s=Machine.Temperature", "Float32", 1,
                2, "℃", "只读", "是", "OPC UA 变量节点"
        ));
        writer.writeRow(Arrays.asList(
                "运行状态", "opc_running", "ns=2;s=Machine.Running", "Boolean", 1,
                0, "", "读写", "是", "服务端节点允许写入时才可写"
        ));

        writer.setSheet("填写说明");
        writer.writeHeadRow(Arrays.asList("字段", "是否必填", "说明"));
        writer.writeRow(Arrays.asList("点位名称", "是", "给人看的名称，例如出口温度"));
        writer.writeRow(Arrays.asList("数据标识", "否", "系统字段名，例如 opc_temperature；不填时按 NodeId 生成"));
        writer.writeRow(Arrays.asList("NodeId", "是", "OPC UA 标准 NodeId，例如 ns=2;s=Machine.Temperature"));
        writer.writeRow(Arrays.asList("数据类型", "是", "Boolean、Int16、UInt16、Int32、UInt32、Float32、String"));
        writer.writeRow(Arrays.asList("读写权限", "否", "只读或读写；最终以 OPC UA 服务端节点写权限为准"));
        writer.writeRow(Arrays.asList("建议", "否", "优先使用页面上的“发现点位”，从地址空间树选择变量节点导入"));
        writer.setSheet("点表模板");
    }

    @PostMapping("/import")
    public Result<PointImportResult> importExcel(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @RequestParam Long deviceId,
                                                 @RequestPart("file") MultipartFile file) throws Exception {
        if (!permissionService.canManageDevice(authorization, deviceId)) {
            return Result.error(403, "当前账号没有该设备的点表管理权限");
        }
        return Result.success(pointService.importFromExcel(deviceId, file.getInputStream()));
    }

    @PostMapping("/import/preview")
    public Result<PointImportPreviewResult> previewImportExcel(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                               @RequestParam Long deviceId,
                                                               @RequestPart("file") MultipartFile file) throws Exception {
        if (!permissionService.canManageDevice(authorization, deviceId)) {
            return Result.error(403, "当前账号没有该设备的点表管理权限");
        }
        return Result.success(pointService.previewImportExcel(deviceId, file.getInputStream()));
    }

    @PostMapping("/import/confirm")
    public Result<PointImportResult> confirmImportExcel(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @RequestParam Long deviceId,
                                                        @RequestBody PointImportConfirmRequest request) {
        if (!permissionService.canManageDevice(authorization, deviceId)) {
            return Result.error(403, "当前账号没有该设备的点表管理权限");
        }
        return Result.success(pointService.confirmImport(deviceId, request.getDuplicateStrategy(), request.getRows()));
    }

    @PutMapping("/{id}")
    public Result<IotCommPoint> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable Long id,
                                       @RequestBody IotCommPoint point) {
        if (!permissionService.canManagePoint(authorization, id)) {
            return Result.error(403, "当前账号没有该点位的管理权限");
        }
        return Result.success(pointService.updatePoint(id, point));
    }

    @GetMapping("/runtime")
    public Result<List<PointRuntimeValue>> runtime(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @RequestParam Long deviceId) {
        if (!permissionService.canViewDevice(authorization, deviceId)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        return Result.success(pointService.listRuntimeValues(deviceId));
    }

    @GetMapping("/history")
    public Result<List<IotPointHistory>> history(@RequestParam(required = false) Long pointId,
                                                 @RequestParam(required = false) Long deviceId,
                                                 @RequestParam(required = false) Long startTime,
                                                 @RequestParam(required = false) Long endTime,
                                                 @RequestParam(required = false) Integer limit) {
        if (pointId == null && deviceId == null) {
            return Result.error(400, "pointId or deviceId is required");
        }
        return Result.success(pointHistoryService.listHistory(pointId, deviceId, null, null, startTime, endTime, limit));
    }

    @PostMapping("/{id}/write")
    public Result<Boolean> write(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @PathVariable Long id,
                                 @RequestBody PointWriteRequest request) {
        if (!permissionService.canOperatePoint(authorization, id)) {
            return Result.error(403, "当前账号没有点位写入权限");
        }
        CurrentUserInfo user = permissionService.currentUser(authorization);
        Boolean result = pointService.writePoint(id, request.getValue());
        auditService.record(user.getUserId(), user.getUsername(), null, null, id, null, "POINT_WRITE", "iot_comm_point", request, result ? "SUCCESS" : "FAILED");
        return Result.success(result);
    }

    @DeleteMapping("/batch")
    public Result<Integer> batchDelete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error(400, "请选择要删除的点位");
        }
        for (Long id : ids) {
            if (!permissionService.canManagePoint(authorization, id)) {
                return Result.error(403, "当前账号没有部分点位的管理权限");
            }
        }
        return Result.success(pointService.deletePoints(ids));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable Long id) {
        if (!permissionService.canManagePoint(authorization, id)) {
            return Result.error(403, "当前账号没有该点位的管理权限");
        }
        return Result.success(pointService.deletePoint(id));
    }
}
