package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.dto.ReportSchemeRequest;
import com.iot.backend.entity.IotReportScheme;
import com.iot.backend.mapper.IotReportSchemeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportSchemeService {

    @Autowired
    private IotReportSchemeMapper schemeMapper;

    public List<IotReportScheme> list(CurrentUserInfo user, String reportType) {
        LambdaQueryWrapper<IotReportScheme> wrapper = new LambdaQueryWrapper<IotReportScheme>()
                .eq(IotReportScheme::getUserId, user.getUserId())
                .orderByAsc(IotReportScheme::getSortNo)
                .orderByDesc(IotReportScheme::getUpdateTime);
        if (reportType != null && reportType.trim().length() > 0) {
            wrapper.eq(IotReportScheme::getReportType, reportType.trim().toUpperCase());
        }
        return schemeMapper.selectList(wrapper);
    }

    public IotReportScheme save(CurrentUserInfo user, ReportSchemeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("报表方案不能为空");
        }
        String reportType = normalizeReportType(request.getReportType());
        String schemeName = trim(request.getSchemeName());
        if (schemeName == null || schemeName.length() == 0) {
            throw new IllegalArgumentException("方案名称不能为空");
        }
        if (schemeName.length() > 50) {
            throw new IllegalArgumentException("方案名称不能超过50个字符");
        }

        long now = System.currentTimeMillis();
        IotReportScheme existing = schemeMapper.selectOne(new LambdaQueryWrapper<IotReportScheme>()
                .eq(IotReportScheme::getUserId, user.getUserId())
                .eq(IotReportScheme::getReportType, reportType)
                .eq(IotReportScheme::getSchemeName, schemeName)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new IotReportScheme();
            existing.setUserId(user.getUserId());
            existing.setReportType(reportType);
            existing.setSchemeName(schemeName);
            existing.setSortNo(100);
            existing.setCreateTime(now);
        }
        existing.setFiltersJson(trim(request.getFiltersJson()));
        existing.setLayoutJson(trim(request.getLayoutJson()));
        existing.setUpdateTime(now);

        if (existing.getId() == null) {
            schemeMapper.insert(existing);
        } else {
            schemeMapper.updateById(existing);
        }
        return existing;
    }

    public void delete(CurrentUserInfo user, Long id) {
        IotReportScheme scheme = schemeMapper.selectById(id);
        if (scheme == null) {
            return;
        }
        if (!user.getUserId().equals(scheme.getUserId())) {
            throw new IllegalArgumentException("不能删除其他账号的报表方案");
        }
        schemeMapper.deleteById(id);
    }

    private String normalizeReportType(String reportType) {
        String value = trim(reportType);
        if (value == null || value.length() == 0) {
            return "HISTORY";
        }
        return value.toUpperCase();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
