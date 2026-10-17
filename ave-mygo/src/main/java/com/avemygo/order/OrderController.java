package com.avemygo.order;

import com.avemygo.common.ApiResponse;
import com.avemygo.order.dto.OrderDtos.CreateOrderRequest;
import com.avemygo.order.dto.OrderDtos.OrderCreated;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口 —— 全部要求登录（不在公开白名单里，这本身就是配置）。
 * 当前登录用户的 userId 从 JWT 盖章后的 Authentication 里取，绝不信任请求参数。
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderCreated> create(@Valid @RequestBody CreateOrderRequest req,
                                            Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());   // JWT subject = userId
        return ApiResponse.ok(orderService.create(userId, req));
    }
}
