package com.iot.backend.service;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.backend.dto.ReportRequest;
import com.iot.backend.dto.ReportResponse;
import com.iot.backend.dto.ReportTemplate;
import com.iot.backend.entity.IotAlarmEvent;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.entity.IotMaintenanceCard;
import com.iot.backend.entity.IotPointHistory;
import com.iot.backend.entity.IotProject;
import com.iot.backend.entity.IotWorkOrder;
import com.iot.backend.mapper.IotAlarmEventMapper;
import com.iot.backend.mapper.IotCommDeviceMapper;
import com.iot.backend.mapper.IotCommPointMapper;
import com.iot.backend.mapper.IotMaintenanceCardMapper;
import com.iot.backend.mapper.IotPointHistoryMapper;
import com.iot.backend.mapper.IotProjectMapper;
import com.iot.backend.mapper.IotWorkOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class ReportService {

    private static final int DEFAULT_LIMIT = 1000;
    private static final int MAX_LIMIT = 5000;

    @Autowired
    private IotPointHistoryMapper historyMapper;

    @Autowired
    private IotAlarmEventMapper alarmMapper;

    @Autowired
    private IotWorkOrderMapper workOrderMapper;

    @Autowired
    private IotMaintenanceCardMapper maintenanceCardMapper;

    @Autowired
    private IotProjectMapper projectMapper;

    @Autowired
    private IotCommDeviceMapper deviceMapper;

    @Autowired
    private IotCommPointMapper pointMapper;

    public List<ReportTemplate> templates() {
        List<ReportTemplate> templates = new ArrayList<>();
        templates.add(template("HISTORY", "历史数据分析报表", "按项目、设备和点位统计采集值、质量和耗时。"));
        templates.add(template("ALARM", "报警事件统计报表", "统计活动报警、恢复报警、报警级别、报警来源和高频设备。"));
        templates.add(template("WORK_ORDER", "工单闭环统计报表", "统计工单状态、优先级、处理人、完成耗时和归档情况。"));
        return templates;
    }

    public ReportResponse preview(ReportRequest request, List<Long> visibleProjectIds) {
        String type = normalizeType(request == null ? null : request.getReportType());
        if ("ALARM".equals(type)) {
            return alarmReport(request, visibleProjectIds);
        }
        if ("WORK_ORDER".equals(type)) {
            return workOrderReport(request, visibleProjectIds);
        }
        return historyReport(request, visibleProjectIds);
    }

    public void exportExcel(ReportRequest request, List<Long> visibleProjectIds, HttpServletResponse response) throws Exception {
        ReportResponse report = preview(request, visibleProjectIds);
        String fileName = URLEncoder.encode(report.getTitle() + "_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".xlsx", StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.renameSheet("指标摘要");
        writer.writeHeadRow(Arrays.asList("指标", "数值", "单位", "级别"));
        for (ReportResponse.Metric metric : report.getMetrics()) {
            writer.writeRow(Arrays.asList(metric.getLabel(), metric.getValue(), nvl(metric.getUnit()), nvl(metric.getLevel())));
        }

        writer.setSheet("明细数据");
        List<String> labels = new ArrayList<>();
        for (ReportResponse.Column column : report.getColumns()) {
            labels.add(column.getLabel());
        }
        writer.writeHeadRow(labels);
        for (Map<String, Object> row : report.getRows()) {
            List<Object> values = new ArrayList<>();
            for (ReportResponse.Column column : report.getColumns()) {
                values.add(row.get(column.getKey()));
            }
            writer.writeRow(values);
        }

        writer.setSheet("图表数据");
        writer.writeHeadRow(Arrays.asList("图表", "名称", "数值"));
        for (ReportResponse.Chart chart : report.getCharts()) {
            for (Map<String, Object> item : chart.getData()) {
                writer.writeRow(Arrays.asList(chart.getTitle(), item.get("name"), item.get("value")));
            }
        }

        ServletOutputStream out = response.getOutputStream();
        writer.flush(out, true);
        writer.close();
        out.close();
    }

    private ReportResponse historyReport(ReportRequest request, List<Long> visibleProjectIds) {
        ReportResponse response = base("HISTORY", "历史数据分析报表", "采集值、采集质量、采集耗时和点位趋势。");
        List<IotPointHistory> rows = listHistory(request, visibleProjectIds);

        Set<Long> deviceIds = new HashSet<>();
        Set<Long> pointIds = new HashSet<>();
        int good = 0;
        long costTotal = 0;
        int costCount = 0;
        Map<String, Bucket> trend = new TreeMap<>();
        Map<String, Bucket> costTrend = new TreeMap<>();
        Map<String, Integer> pointCount = new LinkedHashMap<>();
        Map<String, Integer> qualityCount = new LinkedHashMap<>();

        for (IotPointHistory row : rows) {
            if (row.getDeviceId() != null) {
                deviceIds.add(row.getDeviceId());
            }
            if (row.getPointId() != null) {
                pointIds.add(row.getPointId());
            }
            if ("GOOD".equalsIgnoreCase(row.getQuality())) {
                good++;
            }
            if (row.getCollectCostMs() != null) {
                costTotal += row.getCollectCostMs();
                costCount++;
                Bucket costBucket = costTrend.computeIfAbsent(timeBucket(row.getCollectTime(), request), key -> new Bucket());
                costBucket.sum = costBucket.sum.add(BigDecimal.valueOf(row.getCollectCostMs()));
                costBucket.count++;
            }
            addCount(pointCount, safe(row.getPointLabel(), row.getPointKey()));
            addCount(qualityCount, qualityText(row.getQuality()));
            if (row.getValueNumber() != null && row.getCollectTime() != null) {
                Bucket bucket = trend.computeIfAbsent(timeBucket(row.getCollectTime(), request), key -> new Bucket());
                bucket.sum = bucket.sum.add(row.getValueNumber());
                bucket.count++;
            }
        }

        metric(response, "records", "记录数", String.valueOf(rows.size()), "条", null);
        metric(response, "devices", "设备数", String.valueOf(deviceIds.size()), "台", null);
        metric(response, "points", "点位数", String.valueOf(pointIds.size()), "个", null);
        metric(response, "quality", "采集质量", rows.isEmpty() ? "-" : percent(good, rows.size()), "", rows.isEmpty() || good == rows.size() ? "success" : "warning");
        metric(response, "cost", "平均耗时", costCount == 0 ? "-" : String.format("%.1f", costTotal * 1.0 / costCount), "ms", null);

        if (request != null && request.getPointId() != null) {
            String pointName = rows.isEmpty() ? "当前点位" : safe(rows.get(0).getPointLabel(), rows.get(0).getPointKey());
            ReportResponse.Chart trendChart = chart("trend", pointName + " 数值趋势", "line");
            for (Map.Entry<String, Bucket> entry : trend.entrySet()) {
                mapAdd(trendChart.getData(), entry.getKey(), entry.getValue().avg());
            }
            response.getCharts().add(trendChart);

            ReportResponse.Chart costChart = chart("costTrend", pointName + " 采集耗时", "line");
            for (Map.Entry<String, Bucket> entry : costTrend.entrySet()) {
                mapAdd(costChart.getData(), entry.getKey(), entry.getValue().avg());
            }
            response.getCharts().add(costChart);
        } else {
            response.getCharts().add(countChart("pointCount", "点位采集量排行（前12）", "bar", pointCount, 12));
            response.getCharts().add(countChart("quality", "采集质量分布", "pie", qualityCount, 10));
        }

        column(response, "collectTimeText", "采集时间", 170, "left");
        column(response, "pointLabel", "点位名称", 150, "left");
        column(response, "pointKey", "数据标识", 160, "left");
        column(response, "valueText", "数值", 110, "right");
        column(response, "quality", "质量", 90, "center");
        column(response, "protocolType", "协议", 120, "center");
        column(response, "collectCostMs", "耗时(ms)", 100, "right");
        column(response, "collectorNode", "采集节点", 120, "left");

        for (IotPointHistory row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("collectTimeText", formatTime(row.getCollectTime()));
            map.put("pointLabel", row.getPointLabel());
            map.put("pointKey", row.getPointKey());
            map.put("valueText", row.getValueText());
            map.put("quality", qualityText(row.getQuality()));
            map.put("protocolType", row.getProtocolType());
            map.put("collectCostMs", row.getCollectCostMs());
            map.put("collectorNode", row.getCollectorNode());
            response.getRows().add(map);
        }
        return response;
    }

    private ReportResponse alarmReport(ReportRequest request, List<Long> visibleProjectIds) {
        ReportResponse response = base("ALARM", "报警事件统计报表", "报警级别、状态、来源设备和闭环情况。");
        List<IotAlarmEvent> rows = listAlarms(request, visibleProjectIds);
        Map<Long, IotCommDevice> devices = deviceCache(rows);

        int active = 0;
        int recovered = 0;
        int resolved = 0;
        int highRisk = 0;
        int linkedWorkOrder = 0;
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        Map<String, Integer> severityCount = new LinkedHashMap<>();
        Map<String, Integer> deviceCount = new LinkedHashMap<>();

        for (IotAlarmEvent row : rows) {
            String status = safe(row.getStatus(), "UNKNOWN");
            String severity = safe(row.getSeverity(), "UNKNOWN");
            addCount(statusCount, alarmStatusText(status));
            addCount(severityCount, alarmSeverityText(severity));
            if ("ACTIVE".equalsIgnoreCase(status)) active++;
            if ("RECOVERED".equalsIgnoreCase(status)) recovered++;
            if ("RESOLVED".equalsIgnoreCase(status)) resolved++;
            if ("CRITICAL".equalsIgnoreCase(severity) || "MAJOR".equalsIgnoreCase(severity)) highRisk++;
            if (row.getWorkOrderId() != null) linkedWorkOrder++;
            IotCommDevice device = devices.get(row.getDeviceId());
            addCount(deviceCount, device == null ? "未知设备" : device.getDeviceName());
        }

        metric(response, "total", "报警总数", String.valueOf(rows.size()), "条", null);
        metric(response, "active", "活动报警", String.valueOf(active), "条", active > 0 ? "danger" : "success");
        metric(response, "highRisk", "严重/紧急", String.valueOf(highRisk), "条", highRisk > 0 ? "warning" : "success");
        metric(response, "closed", "已恢复/关闭", String.valueOf(recovered + resolved), "条", null);
        metric(response, "workOrders", "转工单", String.valueOf(linkedWorkOrder), "条", null);

        response.getCharts().add(countChart("status", "报警状态分布", "pie", statusCount, 10));
        response.getCharts().add(countChart("severity", "报警级别分布", "bar", severityCount, 10));
        response.getCharts().add(countChart("devices", "高频报警设备", "bar", deviceCount, 12));

        column(response, "lastTimeText", "最后发生", 170, "left");
        column(response, "status", "状态", 90, "center");
        column(response, "severity", "级别", 90, "center");
        column(response, "title", "报警标题", 180, "left");
        column(response, "deviceName", "设备", 150, "left");
        column(response, "currentValue", "当前值", 110, "right");
        column(response, "thresholdText", "阈值/规则", 150, "left");
        column(response, "occurCount", "次数", 80, "right");

        for (IotAlarmEvent row : rows) {
            IotCommDevice device = devices.get(row.getDeviceId());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("lastTimeText", formatTime(row.getLastTime()));
            map.put("status", alarmStatusText(row.getStatus()));
            map.put("severity", alarmSeverityText(row.getSeverity()));
            map.put("title", row.getTitle());
            map.put("deviceName", device == null ? "-" : device.getDeviceName());
            map.put("currentValue", row.getCurrentValue());
            map.put("thresholdText", row.getThresholdText());
            map.put("occurCount", row.getOccurCount());
            response.getRows().add(map);
        }
        return response;
    }

    private ReportResponse workOrderReport(ReportRequest request, List<Long> visibleProjectIds) {
        ReportResponse response = base("WORK_ORDER", "工单闭环统计报表", "工单状态、优先级、处理人、闭环耗时和资料归档。");
        List<IotWorkOrder> rows = listWorkOrders(request, visibleProjectIds);

        int open = 0;
        int processing = 0;
        int closed = 0;
        int archived = 0;
        int overdue = 0;
        long closeCost = 0;
        int closeCostCount = 0;
        long now = System.currentTimeMillis();
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        Map<String, Integer> priorityCount = new LinkedHashMap<>();
        Map<String, Integer> assigneeCount = new LinkedHashMap<>();

        for (IotWorkOrder row : rows) {
            String status = safe(row.getStatus(), "UNKNOWN");
            addCount(statusCount, workOrderStatusText(status));
            addCount(priorityCount, priorityText(row.getPriority()));
            addCount(assigneeCount, safe(row.getAssigneeName(), "未派单"));
            if ("CLOSED".equalsIgnoreCase(status)) {
                closed++;
                if (row.getCreateTime() != null && row.getCloseTime() != null) {
                    closeCost += row.getCloseTime() - row.getCreateTime();
                    closeCostCount++;
                }
            } else {
                open++;
            }
            if ("PROCESSING".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status)) {
                processing++;
            }
            if (row.getArchiveCardId() != null) {
                archived++;
            }
            if (!"CLOSED".equalsIgnoreCase(status) && row.getPlannedFinishTime() != null && row.getPlannedFinishTime() < now) {
                overdue++;
            }
        }

        metric(response, "total", "工单总数", String.valueOf(rows.size()), "张", null);
        metric(response, "open", "未闭环", String.valueOf(open), "张", open > 0 ? "warning" : "success");
        metric(response, "processing", "处理中", String.valueOf(processing), "张", null);
        metric(response, "closed", "已关闭", String.valueOf(closed), "张", "success");
        metric(response, "overdue", "超期", String.valueOf(overdue), "张", overdue > 0 ? "danger" : "success");
        metric(response, "archiveRate", "归档率", rows.isEmpty() ? "-" : percent(archived, rows.size()), "", null);
        metric(response, "avgClose", "平均闭环", closeCostCount == 0 ? "-" : String.format("%.1f", closeCost / 3600000.0 / closeCostCount), "小时", null);

        response.getCharts().add(countChart("status", "工单状态分布", "pie", statusCount, 10));
        response.getCharts().add(countChart("priority", "优先级分布", "bar", priorityCount, 10));
        response.getCharts().add(countChart("assignee", "处理人工作量", "bar", assigneeCount, 12));

        column(response, "orderNo", "工单号", 170, "left");
        column(response, "status", "状态", 100, "center");
        column(response, "priority", "优先级", 90, "center");
        column(response, "title", "标题", 180, "left");
        column(response, "assigneeName", "处理人", 110, "left");
        column(response, "verifierName", "验收人", 110, "left");
        column(response, "createTimeText", "创建时间", 170, "left");
        column(response, "closeTimeText", "关闭时间", 170, "left");
        column(response, "archive", "资料卡", 90, "center");

        for (IotWorkOrder row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("orderNo", row.getOrderNo());
            map.put("status", workOrderStatusText(row.getStatus()));
            map.put("priority", priorityText(row.getPriority()));
            map.put("title", row.getTitle());
            map.put("assigneeName", row.getAssigneeName());
            map.put("verifierName", row.getVerifierName());
            map.put("createTimeText", formatTime(row.getCreateTime()));
            map.put("closeTimeText", formatTime(row.getCloseTime()));
            map.put("archive", row.getArchiveCardId() == null ? "未归档" : "已归档");
            response.getRows().add(map);
        }
        return response;
    }

    private List<IotPointHistory> listHistory(ReportRequest request, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<IotPointHistory> wrapper = new QueryWrapper<>();
        applyScope(wrapper, request, visibleProjectIds);
        if (request != null && request.getPointId() != null) wrapper.eq("point_id", request.getPointId());
        if (request != null && request.getStartTime() != null) wrapper.ge("collect_time", request.getStartTime());
        if (request != null && request.getEndTime() != null) wrapper.le("collect_time", request.getEndTime());
        wrapper.orderByDesc("collect_time");
        wrapper.last("LIMIT " + limit(request));
        return historyMapper.selectList(wrapper);
    }

    private List<IotAlarmEvent> listAlarms(ReportRequest request, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<IotAlarmEvent> wrapper = new QueryWrapper<>();
        applyScope(wrapper, request, visibleProjectIds);
        if (request != null && request.getPointId() != null) wrapper.eq("point_id", request.getPointId());
        if (request != null && request.getStartTime() != null) wrapper.ge("first_time", request.getStartTime());
        if (request != null && request.getEndTime() != null) wrapper.le("first_time", request.getEndTime());
        wrapper.orderByDesc("last_time");
        wrapper.last("LIMIT " + limit(request));
        return alarmMapper.selectList(wrapper);
    }

    private List<IotWorkOrder> listWorkOrders(ReportRequest request, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<IotWorkOrder> wrapper = new QueryWrapper<>();
        applyScope(wrapper, request, visibleProjectIds);
        if (request != null && request.getPointId() != null) wrapper.eq("point_id", request.getPointId());
        if (request != null && request.getStartTime() != null) wrapper.ge("create_time", request.getStartTime());
        if (request != null && request.getEndTime() != null) wrapper.le("create_time", request.getEndTime());
        wrapper.orderByDesc("create_time");
        wrapper.last("LIMIT " + limit(request));
        return workOrderMapper.selectList(wrapper);
    }

    private void applyScope(QueryWrapper<?> wrapper, ReportRequest request, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null) {
            wrapper.in("project_id", visibleProjectIds);
        }
        if (request == null) {
            return;
        }
        if (request.getProjectId() != null) {
            wrapper.eq("project_id", request.getProjectId());
        }
        if (request.getDeviceId() != null) {
            wrapper.eq("device_id", request.getDeviceId());
        }
        if (request.getGroupId() != null) {
            wrapper.inSql("device_id", "SELECT id FROM iot_comm_device WHERE group_id = " + request.getGroupId());
        }
    }

    private Map<Long, IotCommDevice> deviceCache(List<IotAlarmEvent> alarms) {
        Map<Long, IotCommDevice> devices = new HashMap<>();
        for (IotAlarmEvent alarm : alarms) {
            if (alarm.getDeviceId() != null && !devices.containsKey(alarm.getDeviceId())) {
                devices.put(alarm.getDeviceId(), deviceMapper.selectById(alarm.getDeviceId()));
            }
        }
        return devices;
    }

    private ReportTemplate template(String type, String title, String description) {
        ReportTemplate template = new ReportTemplate();
        template.setReportType(type);
        template.setTitle(title);
        template.setDescription(description);
        template.getSupportedExports().add("EXCEL");
        return template;
    }

    private ReportResponse base(String type, String title, String subtitle) {
        ReportResponse response = new ReportResponse();
        response.setReportType(type);
        response.setTitle(title);
        response.setSubtitle(subtitle);
        response.setGeneratedAt(System.currentTimeMillis());
        return response;
    }

    private ReportResponse.Chart chart(String key, String title, String type) {
        ReportResponse.Chart chart = new ReportResponse.Chart();
        chart.setKey(key);
        chart.setTitle(title);
        chart.setType(type);
        return chart;
    }

    private ReportResponse.Chart countChart(String key, String title, String type, Map<String, Integer> counts, int max) {
        ReportResponse.Chart chart = chart(key, title, type);
        int index = 0;
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        for (Map.Entry<String, Integer> entry : entries) {
            if (index >= max) break;
            mapAdd(chart.getData(), entry.getKey(), entry.getValue());
            index++;
        }
        return chart;
    }

    private void metric(ReportResponse response, String key, String label, String value, String unit, String level) {
        ReportResponse.Metric metric = new ReportResponse.Metric();
        metric.setKey(key);
        metric.setLabel(label);
        metric.setValue(value);
        metric.setUnit(unit);
        metric.setLevel(level);
        response.getMetrics().add(metric);
    }

    private void column(ReportResponse response, String key, String label, Integer width, String align) {
        ReportResponse.Column column = new ReportResponse.Column();
        column.setKey(key);
        column.setLabel(label);
        column.setWidth(width);
        column.setAlign(align);
        response.getColumns().add(column);
    }

    private void mapAdd(List<Map<String, Object>> rows, String name, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("value", value);
        rows.add(map);
    }

    private void addCount(Map<String, Integer> counts, String key) {
        counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
    }

    private int limit(ReportRequest request) {
        Integer value = request == null ? null : request.getLimit();
        if (value == null || value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private String normalizeType(String type) {
        if (type == null || type.trim().length() == 0) {
            return "HISTORY";
        }
        return type.trim().toUpperCase();
    }

    private String timeBucket(Long time, ReportRequest request) {
        if (time == null) {
            return "-";
        }
        long range = request == null || request.getStartTime() == null || request.getEndTime() == null
                ? 0
                : request.getEndTime() - request.getStartTime();
        String pattern = range > 3L * 24 * 3600 * 1000 ? "yyyy-MM-dd" : "MM-dd HH:00";
        return new SimpleDateFormat(pattern).format(new Date(time));
    }

    private String formatTime(Long time) {
        if (time == null) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time));
    }

    private String percent(int part, int total) {
        if (total <= 0) {
            return "-";
        }
        return String.format("%.1f%%", part * 100.0 / total);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String defaultValue) {
        return value == null || value.trim().length() == 0 ? defaultValue : value;
    }

    private String qualityText(String value) {
        String key = safe(value, "UNKNOWN").toUpperCase();
        if ("GOOD".equals(key)) return "良好";
        if ("BAD".equals(key)) return "异常";
        if ("STALE".equals(key)) return "过期";
        if ("UNKNOWN".equals(key)) return "未知";
        return value;
    }

    private String alarmStatusText(String value) {
        String key = safe(value, "UNKNOWN").toUpperCase();
        if ("ACTIVE".equals(key)) return "活动";
        if ("RECOVERED".equals(key)) return "已恢复";
        if ("RESOLVED".equals(key)) return "已关闭";
        if ("CLOSED".equals(key)) return "已关闭";
        if ("UNKNOWN".equals(key)) return "未知";
        return value;
    }

    private String alarmSeverityText(String value) {
        String key = safe(value, "UNKNOWN").toUpperCase();
        if ("INFO".equals(key)) return "提示";
        if ("WARN".equals(key)) return "预警";
        if ("WARNING".equals(key)) return "预警";
        if ("MINOR".equals(key)) return "一般";
        if ("MAJOR".equals(key)) return "严重";
        if ("CRITICAL".equals(key)) return "紧急";
        if ("UNKNOWN".equals(key)) return "未知";
        return value;
    }

    private String workOrderStatusText(String value) {
        String key = safe(value, "UNKNOWN").toUpperCase();
        if ("CREATED".equals(key)) return "待派单";
        if ("DISPATCHED".equals(key)) return "待接单";
        if ("ACCEPTED".equals(key)) return "已接单";
        if ("PROCESSING".equals(key)) return "处理中";
        if ("PENDING_VERIFY".equals(key)) return "待验收";
        if ("CLOSED".equals(key)) return "已关闭";
        if ("ARCHIVED".equals(key)) return "已归档";
        if ("UNKNOWN".equals(key)) return "未知";
        return value;
    }

    private String priorityText(String value) {
        String key = safe(value, "MINOR").toUpperCase();
        if ("INFO".equals(key)) return "提示";
        if ("WARN".equals(key) || "WARNING".equals(key)) return "预警";
        if ("MINOR".equals(key)) return "一般";
        if ("MAJOR".equals(key)) return "严重";
        if ("CRITICAL".equals(key)) return "紧急";
        if ("LOW".equals(key)) return "预警";
        if ("NORMAL".equals(key)) return "一般";
        if ("HIGH".equals(key)) return "严重";
        if ("URGENT".equals(key)) return "紧急";
        return value;
    }

    private static class Bucket {
        private BigDecimal sum = BigDecimal.ZERO;
        private int count;

        private BigDecimal avg() {
            if (count <= 0) {
                return BigDecimal.ZERO;
            }
            return sum.divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_HALF_UP);
        }
    }
}
