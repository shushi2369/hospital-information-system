package com.his.modules.ae.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.ae.entity.AeEvent;
import com.his.modules.ae.mapper.AeEventMapper;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 不良事件服务（四期三批）：任何员工上报 → 质控分派 → 整改 → 闭环确认。 */
@Service
@RequiredArgsConstructor
public class AeService {
    private final AeEventMapper eventMapper;
    private final PltService pltService;
    private final IdGenerator idGenerator;

    /** 上报（AE-01）：任何员工 */
    @Transactional
    public String report(AeEvent event) {
        if (event.getEventType() == null || event.getEventType() < 1 || event.getEventType() > 7) {
            throw new BizException(ErrorCode.A0001, "事件类型取值 1~7");
        }
        if (event.getSeverity() == null || event.getSeverity() < 1 || event.getSeverity() > 4) {
            throw new BizException(ErrorCode.A0001, "严重程度取值 1~4");
        }
        event.setEventNo(idGenerator.next("AE"));
        event.setReporterId(CurrentUser.id());
        event.setStatus(10);
        eventMapper.insert(event);
        pltService.recordEvent("ae.reported", event.getEventNo(),
                "{\"type\":" + event.getEventType() + ",\"severity\":" + event.getSeverity() + "}");
        return event.getEventNo();
    }

    /** 分页（AE-04） */
    public PageResult<AeEvent> page(com.his.common.PageQuery query, Integer status, Integer eventType) {
        Page<AeEvent> page = eventMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<AeEvent>()
                        .eq(status != null, AeEvent::getStatus, status)
                        .eq(eventType != null, AeEvent::getEventType, eventType)
                        .orderByAsc(AeEvent::getStatus).orderByDesc(AeEvent::getId));
        return PageResult.of(page);
    }

    /** 质控分派（AE-02）：10 → 20 */
    @Transactional
    public void assign(Long id) {
        AeEvent event = requireEvent(id);
        if (event.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "事件不在已上报状态");
        }
        event.setStatus(20);
        event.setQcId(CurrentUser.id());
        if (eventMapper.updateById(event) != 1) {
            throw new BizException(ErrorCode.A0008, "事件状态已变化，请刷新后重试");
        }
        pltService.recordEvent("ae.assigned", event.getEventNo(), "{}");
    }

    /** 整改（AE-03）：20 → 30 */
    @Transactional
    public void rectify(Long id, String handlerNote) {
        AeEvent event = requireEvent(id);
        if (event.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "事件未分派，不能整改");
        }
        event.setStatus(30);
        event.setHandlerNote(handlerNote);
        event.setHandlerId(CurrentUser.id());
        if (eventMapper.updateById(event) != 1) {
            throw new BizException(ErrorCode.A0008, "事件状态已变化，请刷新后重试");
        }
    }

    /** 闭环确认（AE-05）：30 → 40（质控确认） */
    @Transactional
    public void close(Long id) {
        AeEvent event = requireEvent(id);
        if (event.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "事件未整改，不能闭环");
        }
        event.setStatus(40);
        event.setClosedTime(LocalDateTime.now());
        if (eventMapper.updateById(event) != 1) {
            throw new BizException(ErrorCode.A0008, "事件状态已变化，请刷新后重试");
        }
        pltService.recordEvent("ae.closed", event.getEventNo(), "{}");
    }

    private AeEvent requireEvent(Long id) {
        AeEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ErrorCode.A0001, "不良事件不存在");
        }
        return event;
    }
}
