package com.avemygo.admin;

import com.avemygo.admin.dto.AdminUserDtos.UserView;
import com.avemygo.common.BizException;
import com.avemygo.user.User;
import com.avemygo.user.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端用户业务：列表（含消费统计）+ 封禁/解封。
 *
 * 防锁死规则（很重要）：
 *  - 不能封禁自己（否则管理员把自己踢出去）
 *  - 不能封禁任何 ADMIN 账号（防止唯一管理员被普通管理员封掉）
 *  - 状态只允许 0/1；封禁后登录会被 AuthService 拦成 40301
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<UserView> search(String keyword, int page, int pageSize) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<User> users = userRepository.search(kw,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(pageSize, 1), 100),
                        Sort.by(Sort.Direction.DESC, "id")));

        Map<Long, BigDecimal> spent = new HashMap<>();
        Map<Long, Long> counts = new HashMap<>();
        var ids = users.map(User::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : userRepository.paidStats(ids)) {
                Long userId = ((Number) row[0]).longValue();
                spent.put(userId, row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString()));
                counts.put(userId, ((Number) row[2]).longValue());
            }
        }
        return users.map(u -> new UserView(u.getId(), u.getUsername(), u.getNickname(),
                u.getRole(), u.getStatus(),
                counts.getOrDefault(u.getId(), 0L),
                spent.getOrDefault(u.getId(), BigDecimal.ZERO),
                u.getCreatedAt()));
    }

    @Transactional
    public UserView updateStatus(Long adminId, Long targetId, byte status) {
        if (status != 0 && status != 1) {
            throw new BizException(40001, "状态只能是 0 或 1");
        }
        if (targetId.equals(adminId)) {
            throw new BizException(40001, "不能修改自己的账号状态");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new BizException(40401, "用户不存在"));
        if ("ADMIN".equals(target.getRole())) {
            throw new BizException(40001, "不能封禁管理员账号");
        }
        target.setStatus(status);
        User saved = userRepository.save(target);
        log.info("管理端修改用户状态 adminId={} userId={} status={}", adminId, targetId, status);
        return new UserView(saved.getId(), saved.getUsername(), saved.getNickname(),
                saved.getRole(), saved.getStatus(), 0, BigDecimal.ZERO, saved.getCreatedAt());
    }
}
