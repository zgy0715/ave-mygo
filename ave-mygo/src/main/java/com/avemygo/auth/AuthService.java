package com.avemygo.auth;

import com.avemygo.auth.dto.RegisterRequest;
import com.avemygo.common.BizException;
import com.avemygo.user.User;
import com.avemygo.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证业务层：注册/登录的业务逻辑全在这里。
 * 分层纪律：Controller 不写逻辑，Repository 不做判断，判断都在 Service。
 */
@Service
@RequiredArgsConstructor   // Lombok：为所有 final 字段生成构造器，实现构造器注入
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 注册：查重 → 加密 → 落库，返回新建用户 id */
    public Long register(RegisterRequest req) {
        // 1. 查重（并发下仍可能重复，靠数据库 uk_username 唯一索引兜底）
        if (userRepository.existsByUsername(req.username())) {
            throw new BizException(40904, "用户名已存在");
        }

        // 2. 组装实体并加密密码
        User user = new User();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname() == null || req.nickname().isBlank()
                ? req.username() : req.nickname());

        // 3. 落库
        return userRepository.save(user).getId();
    }
}
