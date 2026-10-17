package com.avemygo.order;

import com.avemygo.common.ApiResponse;
import com.avemygo.order.dto.OrderDtos.CreateOrderRequest;
import com.avemygo.order.dto.OrderDtos.OrderCreated;
import com.avemygo.order.dto.OrderDtos.OrderView;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** 我的订单列表 */
    @GetMapping("/my")
    public ApiResponse<List<OrderView>> my(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(orderService.myOrders(userId));
    }

    /** 模拟支付（幂等：重复调用返回同一结果） */
    @PostMapping("/{orderNo}/pay")
    public ApiResponse<OrderView> pay(@PathVariable String orderNo, Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(orderService.pay(orderNo, userId));
    }

    /** 用户取消订单（回滚库存） */
    @PatchMapping("/{orderNo}/cancel")
    public ApiResponse<OrderView> cancel(@PathVariable String orderNo, Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ApiResponse.ok(orderService.cancel(orderNo, userId));
    }
}
