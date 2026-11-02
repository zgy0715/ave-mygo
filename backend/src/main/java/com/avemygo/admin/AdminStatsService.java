package com.avemygo.admin;

import com.avemygo.admin.dto.AdminStatsDtos.DailyPoint;
import com.avemygo.admin.dto.AdminStatsDtos.Overview;
import com.avemygo.admin.dto.AdminStatsDtos.SessionOccupancy;
import com.avemygo.admin.dto.AdminStatsDtos.Totals;
import com.avemygo.event.TicketTypeRepository;
import com.avemygo.order.TicketOrderRepository;
import com.avemygo.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据看板聚合。
 * 口径（很重要，避免"营收虚高"）：
 *  - 营收/票数/订单数只统计 PAID，退款单（REFUNDED）不计入
 *  - 每日趋势按 paid_at 归集，缺失日期补 0，前端可直接画折线
 *  - 上座率 = 各场次 sold_count / total_stock（按票档求和），>100% 不会出现（CHECK 约束保证）
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private static final int MAX_DAYS = 90;
    private static final int MAX_SESSIONS = 20;

    private final TicketOrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Overview overview(int days) {
        int span = Math.min(Math.max(days, 1), MAX_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(span - 1L);

        Totals totals = new Totals(
                orderRepository.countByStatus("PAID"),
                nz(orderRepository.totalRevenue()),
                orderRepository.totalTicketsSold(),
                orderRepository.countByStatus("REFUNDED"),
                userRepository.count());

        // 每日趋势：SQL 只返回有数据的日期，这里补齐空白日期
        Map<LocalDate, DailyPoint> byDate = new HashMap<>();
        for (Object[] row : orderRepository.dailyPaidSince(from.atStartOfDay())) {
            LocalDate date = toLocalDate(row[0]);
            long orders = ((Number) row[1]).longValue();
            BigDecimal revenue = row[2] == null ? BigDecimal.ZERO : new BigDecimal(row[2].toString());
            byDate.put(date, new DailyPoint(date, orders, revenue));
        }
        List<DailyPoint> daily = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            LocalDate d = from.plusDays(i);
            daily.add(byDate.getOrDefault(d, new DailyPoint(d, 0, BigDecimal.ZERO)));
        }

        // 场次上座率：最近 MAX_SESSIONS 场（按开演时间倒序）
        List<SessionOccupancy> sessions = ticketTypeRepository.sessionOccupancy().stream()
                .map(row -> {
                    long sessionId = ((Number) row[0]).longValue();
                    String sessionName = (String) row[1];
                    String eventTitle = (String) row[2];
                    LocalDateTime startTime = row[3] == null ? null : ((java.time.LocalDateTime) row[3]);
                    int total = ((Number) row[4]).intValue();
                    int sold = ((Number) row[5]).intValue();
                    double occupancy = total == 0 ? 0
                            : BigDecimal.valueOf(sold * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
                    return new SessionOccupancy(sessionId, sessionName, eventTitle, startTime, total, sold, occupancy);
                })
                .sorted(Comparator.comparing(SessionOccupancy::startTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_SESSIONS)
                .toList();

        return new Overview(totals, daily, sessions);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof Date d) return d.toLocalDate();
        if (o instanceof java.util.Date d) return new java.sql.Date(d.getTime()).toLocalDate();
        return LocalDate.parse(o.toString());
    }
}
