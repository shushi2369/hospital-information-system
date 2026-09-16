package com.his.modules.doc.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.doc.dto.OrderCreateRequest;
import com.his.modules.doc.dto.OrderQuery;
import com.his.modules.doc.dto.ReviewOrderRequest;
import com.his.modules.doc.dto.SkinTestRequest;
import com.his.modules.doc.dto.StopOrderRequest;
import com.his.modules.doc.entity.DocOrder;
import com.his.modules.doc.entity.DocOrderExec;
import com.his.modules.doc.service.DocOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 医嘱闭环住院段接口（O-01~O-12）。
 */
@RestController
@RequestMapping("/api/v1/doc")
@RequiredArgsConstructor
public class DocOrderController {
    private final DocOrderService docOrderService;

    @PostMapping("/orders")
    @PreAuthorize("@ss.hasPerm('doc:order:create')")
    @Idempotent
    @AuditLog(module = "doc", action = "开医嘱", bizType = "doc_order")
    public R<String> create(@Valid @RequestBody OrderCreateRequest req) {
        return R.ok(docOrderService.create(req));
    }

    @GetMapping("/orders")
    @PreAuthorize("@ss.hasPerm('doc:order:query')")
    public R<PageResult<DocOrder>> page(OrderQuery query) {
        return R.ok(docOrderService.page(query));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@ss.hasPerm('doc:order:query')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(docOrderService.detail(id));
    }

    @PostMapping("/orders/{id}/review")
    @PreAuthorize("@ss.hasPerm('pharmacy:review:do')")
    @Idempotent
    @AuditLog(module = "doc", action = "医嘱审核", bizType = "doc_order")
    public R<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewOrderRequest req) {
        docOrderService.review(id, req);
        return R.ok();
    }

    @PostMapping("/orders/{id}/stop")
    @PreAuthorize("@ss.hasPerm('doc:order:stop')")
    @Idempotent
    @AuditLog(module = "doc", action = "停医嘱", bizType = "doc_order")
    public R<Void> stop(@PathVariable Long id, @Valid @RequestBody StopOrderRequest req) {
        docOrderService.stop(id, req);
        return R.ok();
    }

    @PostMapping("/orders/{id}/resume")
    @PreAuthorize("@ss.hasPerm('doc:order:stop')")
    @Idempotent
    @AuditLog(module = "doc", action = "恢复医嘱", bizType = "doc_order")
    public R<Void> resume(@PathVariable Long id) {
        docOrderService.resume(id);
        return R.ok();
    }

    @PostMapping("/orders/{id}/void")
    @PreAuthorize("@ss.hasPerm('doc:order:create')")
    @Idempotent
    @AuditLog(module = "doc", action = "作废医嘱", bizType = "doc_order")
    public R<Void> voidOrder(@PathVariable Long id, @RequestBody(required = false) StopOrderRequest req) {
        docOrderService.voidOrder(id, req == null ? new StopOrderRequest() : req);
        return R.ok();
    }

    @PostMapping("/orders/{id}/dispense")
    @PreAuthorize("@ss.hasPerm('pharmacy:dispense:do')")
    @Idempotent
    @AuditLog(module = "doc", action = "住院摆药出库", bizType = "doc_order")
    public R<Void> dispense(@PathVariable Long id) {
        docOrderService.dispense(id);
        return R.ok();
    }

    @GetMapping("/orders/review-queue")
    @PreAuthorize("@ss.hasPerm('pharmacy:review:query')")
    public R<List<DocOrder>> reviewQueue() {
        return R.ok(docOrderService.reviewQueue());
    }

    @GetMapping("/executions/todo")
    @PreAuthorize("@ss.hasPerm('nur:exec:do')")
    public R<List<DocOrderExec>> todo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate execDate) {
        return R.ok(docOrderService.todo(execDate == null ? LocalDate.now() : execDate));
    }

    @PostMapping("/executions/{id}/do")
    @PreAuthorize("@ss.hasPerm('nur:exec:do')")
    @Idempotent
    @AuditLog(module = "doc", action = "医嘱执行", bizType = "doc_order_exec")
    public R<Void> execute(@PathVariable Long id) {
        docOrderService.execute(id);
        return R.ok();
    }

    @PostMapping("/skin-tests/{execId}")
    @PreAuthorize("@ss.hasPerm('nur:exec:do')")
    @Idempotent
    @AuditLog(module = "doc", action = "皮试登记", bizType = "doc_order_exec")
    public R<Void> skinTest(@PathVariable Long execId, @Valid @RequestBody SkinTestRequest req) {
        docOrderService.skinTest(execId, req);
        return R.ok();
    }
}
