package com.his.modules.alert.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageQuery;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.alert.entity.AlertCritical;
import com.his.modules.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 危急值闭环接口（W-01~W-04）。
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final AlertService alertService;

    @GetMapping
    @PreAuthorize("@ss.hasPerm('alert:query')")
    public R<PageResult<AlertCritical>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return R.ok(alertService.page(status, query));
    }

    @PostMapping("/{id}/notify")
    @PreAuthorize("@ss.hasPerm('alert:notify')")
    @Idempotent
    @AuditLog(module = "alert", action = "危急值通知", bizType = "alert_critical")
    public R<Void> notify(@PathVariable Long id) {
        alertService.notify(id);
        return R.ok();
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPerm('alert:confirm')")
    @Idempotent
    @AuditLog(module = "alert", action = "危急值确认", bizType = "alert_critical")
    public R<Void> confirm(@PathVariable Long id) {
        alertService.confirm(id);
        return R.ok();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.hasPerm('alert:confirm')")
    @Idempotent
    @AuditLog(module = "alert", action = "危急值闭环", bizType = "alert_critical")
    public R<Void> close(@PathVariable Long id,
                         @RequestBody(required = false) java.util.Map<String, String> body) {
        alertService.close(id, body == null ? null : body.get("handleNote"));
        return R.ok();
    }
}
