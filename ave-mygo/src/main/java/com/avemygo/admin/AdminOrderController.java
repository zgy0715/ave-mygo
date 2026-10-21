package com.avemygo.admin;

import com.avemygo.admin.dto.AdminOrderDtos.AdminOrderView;
import com.avemygo.admin.dto.AdminOrderDtos.RefundRequest;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端订单接口（仅 ADMIN）：检索 + 退款（回滚库存）。 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService service;

    @GetMapping
    public ApiResponse<Page<AdminOrderView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.search(status, eventId, keyword, page, pageSize));
    }

    @PatchMapping("/{orderNo}/refund")
    public ApiResponse<AdminOrderView> refund(@PathVariable String orderNo,
                                              @Valid @RequestBody RefundRequest req) {
        return ApiResponse.ok(service.refund(orderNo, req.reason()));
    }
}
