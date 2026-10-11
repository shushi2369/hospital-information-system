package com.his.modules.plt.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.modules.plt.entity.PltEventLog;
import com.his.modules.plt.entity.PltMasterIndex;
import com.his.modules.plt.mapper.PltEventLogMapper;
import com.his.modules.plt.mapper.PltMasterIndexMapper;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台底座跨模块应用服务：各模块经此注册 EMPI / 写事件 / 查事件链。
 */
@Service
@RequiredArgsConstructor
public class PltAppService {
    private final PltMasterIndexMapper masterIndexMapper;
    private final PltEventLogMapper eventLogMapper;
    private final PltService pltService;

    /** 建档后注册主索引（幂等） */
    public String registerMpi(Long patientId) {
        return pltService.registerMpi(patientId);
    }

    public PltMasterIndex getByPatientId(Long patientId) {
        return masterIndexMapper.selectByPatientId(patientId);
    }

    public PltMasterIndex getByMpiNo(String mpiNo) {
        return masterIndexMapper.selectOne(new LambdaQueryWrapper<PltMasterIndex>()
                .eq(PltMasterIndex::getMpiNo, mpiNo).last("LIMIT 1"));
    }

    /** 按过滤条件分页查询主索引（过滤集可为 null=不过滤） */
    public Page<PltMasterIndex> search(LambdaQueryWrapper<PltMasterIndex> wrapper, Page<PltMasterIndex> page) {
        return masterIndexMapper.selectPage(page, wrapper);
    }

    public PageResult<PltEventLog> listEvents(String eventType, String bizNo,
                                              LocalDateTime start, LocalDateTime end,
                                              long pageNum, long pageSize) {
        // 一百一十六轮 cdss【11】：硬 LIMIT 500 改真分页——事件审计超 500 条时旧数据不可见
        Page<PltEventLog> page = eventLogMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)),
                new LambdaQueryWrapper<PltEventLog>()
                        .eq(eventType != null && !eventType.isBlank(), PltEventLog::getEventType, eventType)
                        .eq(bizNo != null && !bizNo.isBlank(), PltEventLog::getBizNo, bizNo)
                        .ge(start != null, PltEventLog::getCreatedAt, start)
                        .lt(end != null, PltEventLog::getCreatedAt, end)
                        .orderByDesc(PltEventLog::getId));
        return PageResult.of(page);
    }
}
