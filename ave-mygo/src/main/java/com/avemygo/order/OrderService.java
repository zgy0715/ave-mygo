package com.avemygo.order;

import com.avemygo.common.BizException;
import com.avemygo.event.LiveSession;
import com.avemygo.event.LiveSessionRepository;
import com.avemygo.event.TicketType;
import com.avemygo.event.TicketTypeRepository;
import com.avemygo.order.dto.OrderDtos.CreateOrderRequest;
import com.avemygo.order.dto.OrderDtos.OrderCreated;
import com.avemygo.order.dto.OrderDtos.OrderView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单业务层 —— 防超卖三件套在此落地：
 * ① 原子 UPDATE 扣库存（TicketTypeRepository.increaseSold，检查与扣减一次完成）
 * ② 同一事务：扣减与建单同生共死
 * ③ order_no 唯一索引兜底
 * 校验顺序：场次状态 → 票档归属与启用 → 限购 → 扣减 → 建单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int PAY_WINDOW_MINUTES = 15;

    private final LiveSessionRepository sessionRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketOrderRepository orderRepository;

    @Transactional
    public OrderCreated create(Long userId, CreateOrderRequest req) {
        int qty = req.quantity();

        // 1. 场次必须处于销售中
        LiveSession session = sessionRepository.findById(req.sessionId())
                .orElseThrow(() -> new BizException(40401, "场次不存在"));
        if (!"ONSALE".equals(session.getStatus())) {
            throw new BizException(40902, "该场次当前不可购票");
        }

        // 2. 票档必须属于该场次且启用
        TicketType ticketType = ticketTypeRepository.findById(req.ticketTypeId())
                .orElseThrow(() -> new BizException(40401, "票档不存在"));
        if (!ticketType.getSessionId().equals(session.getId())) {
            throw new BizException(40001, "票档与场次不匹配");
        }
        if (ticketType.getStatus() != 1) {
            throw new BizException(40902, "该票档已停售");
        }

        // 3. 限购：本笔数量 + 该用户该场次活跃订单合计 不得超过 max_per_user
        int alreadyBought = orderRepository.sumActiveQuantity(userId, session.getId());
        if (alreadyBought + qty > ticketType.getMaxPerUser()) {   // Byte 参与算术自动提升为 int
            throw new BizException(40903, "超出限购数量：该场次最多购买 " + ticketType.getMaxPerUser() + " 张");
        }

        // 4. 原子扣库存：影响行数 0 = 余票不足（并发安全的唯一真相来源）
        if (ticketTypeRepository.increaseSold(ticketType.getId(), qty) == 0) {
            throw new BizException(40901, "余票不足");
        }

        // 5. 同一事务内建单（上方抛异常则扣减一并回滚）
        TicketOrder order = new TicketOrder();
        order.setOrderNo(genOrderNo());
        order.setUserId(userId);
        order.setSessionId(session.getId());
        order.setEventId(session.getEventId());
        order.setTicketTypeId(ticketType.getId());
        order.setQuantity((byte) qty);
        order.setUnitPrice(ticketType.getPrice());
        order.setTotalAmount(ticketType.getPrice().multiply(BigDecimal.valueOf(qty)));
        order.setPayDeadline(LocalDateTime.now().plusMinutes(PAY_WINDOW_MINUTES));
        order = orderRepository.save(order);

        return new OrderCreated(order.getOrderNo(), order.getStatus(),
                order.getUnitPrice(), order.getTotalAmount(), order.getQuantity().intValue(),
                order.getPayDeadline());
    }

    /** BL + yyyyMMdd + 10 位随机数；uk_order_no 唯一索引兜底极端碰撞 */
    private static String genOrderNo() {
        String date = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        long random = ThreadLocalRandom.current().nextLong(10_000_000_000L);
        return "BL" + date + String.format("%010d", random);
    }

    // ==================== 订单状态机（docs/05 §4.2） ====================

    /** 查本人的订单，他人订单一律 40401（不泄露存在性） */
    private TicketOrder findOwned(String orderNo, Long userId) {
        TicketOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(40401, "订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BizException(40401, "订单不存在");
        }
        return order;
    }

    /** 模拟支付：PENDING → PAID。幂等：重复支付返回原结果不报错 */
    @Transactional
    public OrderView pay(String orderNo, Long userId) {
        TicketOrder order = findOwned(orderNo, userId);
        if ("PAID".equals(order.getStatus())) {
            return toView(order);                       // 幂等：已是支付状态直接返回
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BizException(40902, "订单当前状态不可支付");
        }
        if (LocalDateTime.now().isAfter(order.getPayDeadline())) {
            throw new BizException(40902, "订单已超时，请重新下单");   // 任务延迟时的兜底校验
        }
        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        return toView(order);
    }

    /** 用户取消：PENDING → CANCELLED + 回滚库存（同事务） */
    @Transactional
    public OrderView cancel(String orderNo, Long userId) {
        TicketOrder order = findOwned(orderNo, userId);
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BizException(40902, "订单当前状态不可取消");
        }
        order.setStatus("CANCELLED");
        order.setCancelledAt(LocalDateTime.now());
        rollbackStock(order);
        return toView(order);
    }

    /** 我的订单列表 */
    @Transactional(readOnly = true)
    public List<OrderView> myOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toView).toList();
    }

    /**
     * 超时取消定时任务：每 60 秒扫一批 PENDING 且过期的订单。
     * 与支付接口的 payDeadline 校验互为双保险，任务延迟不会导致超卖。
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expirePendingOrders() {
        List<TicketOrder> expired = orderRepository
                .findByStatusAndPayDeadlineBefore("PENDING_PAYMENT", LocalDateTime.now());
        for (TicketOrder order : expired) {
            order.setStatus("EXPIRED");
            order.setCancelledAt(LocalDateTime.now());
            rollbackStock(order);
            log.warn("订单超时取消: orderNo={}, qty={}", order.getOrderNo(), order.getQuantity());
        }
    }

    /** 库存回滚：影响行数 0 属于理论外事故，必须留 ERROR 日志追查 */
    private void rollbackStock(TicketOrder order) {
        int rows = ticketTypeRepository.decreaseSold(order.getTicketTypeId(), order.getQuantity());
        if (rows == 0) {
            log.error("库存回滚异常：sold_count < quantity，orderNo={}", order.getOrderNo());
        }
    }

    private OrderView toView(TicketOrder o) {
        return new OrderView(o.getOrderNo(), o.getStatus(), o.getQuantity().intValue(),
                o.getUnitPrice(), o.getTotalAmount(), o.getPayDeadline(),
                o.getPaidAt(), o.getCancelledAt(), o.getRefundedAt(), o.getRefundReason(),
                o.getCreatedAt());
    }
}
