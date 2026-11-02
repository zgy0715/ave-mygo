package com.avemygo.admin;

import com.avemygo.admin.dto.AdminOrderDtos.AdminOrderView;
import com.avemygo.common.BizException;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.event.LiveSession;
import com.avemygo.event.LiveSessionRepository;
import com.avemygo.event.TicketType;
import com.avemygo.event.TicketTypeRepository;
import com.avemygo.order.TicketOrder;
import com.avemygo.order.TicketOrderRepository;
import com.avemygo.order.UserSessionQuotaRepository;
import com.avemygo.user.User;
import com.avemygo.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端订单业务：检索 + 退款（docs/04 管理端 P0）。
 *
 * 退款规则（与用户取消同口径）：
 *  - 只有 PAID 可退；PENDING 需用户自行取消，REFUNDED/CANCELLED/EXPIRED 不可退
 *  - 退款必须在同一事务里回滚 ticket_type.sold_count，否则库存永久虚占
 *  - 退款原因必填，落库供审计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final TicketOrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final LiveSessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final UserSessionQuotaRepository quotaRepository;

    @Transactional(readOnly = true)
    public Page<AdminOrderView> search(String status, Long eventId, String keyword,
                                       int page, int pageSize) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<TicketOrder> orders = orderRepository.adminSearch(
                (status == null || status.isBlank()) ? null : status.trim().toUpperCase(),
                eventId, kw,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(pageSize, 1), 100),
                        Sort.by(Sort.Direction.DESC, "id")));

        // 批量取关联名称，避免逐行 N+1
        Map<Long, String> usernames = userRepository.findAllById(
                        orders.map(TicketOrder::getUserId).stream().distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        Map<Long, LiveSession> sessions = sessionRepository.findAllById(
                        orders.map(TicketOrder::getSessionId).stream().distinct().toList())
                .stream().collect(Collectors.toMap(LiveSession::getId, Function.identity()));
        Map<Long, LiveEvent> events = eventRepository.findAllById(
                        orders.map(TicketOrder::getEventId).stream().distinct().toList())
                .stream().collect(Collectors.toMap(LiveEvent::getId, Function.identity()));
        Map<Long, TicketType> types = ticketTypeRepository.findAllById(
                        orders.map(TicketOrder::getTicketTypeId).stream().distinct().toList())
                .stream().collect(Collectors.toMap(TicketType::getId, Function.identity()));

        return orders.map(o -> new AdminOrderView(
                o.getOrderNo(), o.getUserId(), usernames.get(o.getUserId()),
                events.containsKey(o.getEventId()) ? events.get(o.getEventId()).getTitle() : null,
                sessions.containsKey(o.getSessionId()) ? sessions.get(o.getSessionId()).getName() : null,
                types.containsKey(o.getTicketTypeId()) ? types.get(o.getTicketTypeId()).getName() : null,
                o.getQuantity().intValue(), o.getUnitPrice(), o.getTotalAmount(), o.getStatus(),
                o.getCreatedAt(), o.getPaidAt(), o.getPayDeadline(),
                o.getRefundedAt(), o.getRefundReason()));
    }

    @Transactional
    public AdminOrderView refund(String orderNo, String reason) {
        TicketOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(40401, "订单不存在"));
        if (!"PAID".equals(order.getStatus())) {
            throw new BizException(40902, "订单当前状态不可退款（仅已支付可退）");
        }
        // 条件 UPDATE：两个管理员同时点退款，只有一个能拿到行；抢不到的直接判失败
        String trimmedReason = reason == null ? null : reason.trim();
        int rows = orderRepository.markRefunded(orderNo, LocalDateTime.now(), trimmedReason);
        if (rows == 0) {
            throw new BizException(40902, "订单当前状态不可退款（可能已被退款）");
        }
        // 状态转换成功之后才回滚库存与配额——顺序反了会导致重复退款把库存刷成负数
        int stockRows = ticketTypeRepository.decreaseSold(order.getTicketTypeId(), order.getQuantity());
        if (stockRows == 0) {
            log.error("退款回滚库存异常：sold_count < quantity, orderNo={}", orderNo);
        }
        int quotaRows = quotaRepository.release(order.getUserId(), order.getSessionId(),
                order.getQuantity());
        if (quotaRows == 0) {
            log.warn("退款释放配额未命中（可能已释放）orderNo={}", orderNo);
        }
        order = orderRepository.findByOrderNo(orderNo).orElseThrow(
                () -> new BizException(40401, "订单不存在"));
        log.info("管理端退款 orderNo={} qty={} amount={} reason={}",
                orderNo, order.getQuantity(), order.getTotalAmount(), order.getRefundReason());
        return new AdminOrderView(order.getOrderNo(), order.getUserId(),
                userRepository.findById(order.getUserId()).map(User::getUsername).orElse(null),
                eventRepository.findById(order.getEventId()).map(LiveEvent::getTitle).orElse(null),
                sessionRepository.findById(order.getSessionId()).map(LiveSession::getName).orElse(null),
                ticketTypeRepository.findById(order.getTicketTypeId()).map(TicketType::getName).orElse(null),
                order.getQuantity().intValue(), order.getUnitPrice(), order.getTotalAmount(),
                order.getStatus(), order.getCreatedAt(), order.getPaidAt(), order.getPayDeadline(),
                order.getRefundedAt(), order.getRefundReason());
    }
}
