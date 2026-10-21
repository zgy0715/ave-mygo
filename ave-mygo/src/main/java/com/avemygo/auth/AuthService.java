package com.avemygo.auth;

import com.avemygo.auth.dto.LoginRequest;
import com.avemygo.auth.dto.LoginResponse;
import com.avemygo.auth.dto.RegisterRequest;
import com.avemygo.common.BizException;
import com.avemygo.user.User;
import com.avemygo.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final JwtService jwtService;

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

        // 3. 落库：并发场景 exists 查询可能同时通过，唯一索引会拒第二笔，
        //    这里把它翻译成业务错误 40904，而不是抛 500
        try {
            return userRepository.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException e) {
            throw new BizException(40904, "用户名已存在");
        }
    }

    /** 登录：查用户 → 验密码 → 查封禁 → 签发 token */
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username()).orElse(null);

        // 安全细节：用户不存在 和 密码错误 返回同一句话，不给撞库者提示
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BizException(40101, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BizException(40301, "账号已被封禁");
        }

        String token = jwtService.generate(user.getId(), user.getRole());
        return new LoginResponse(token, 24 * 3600,
                new LoginResponse.UserBrief(user.getId(), user.getUsername(),
                        user.getNickname(), user.getRole()));
    }
}
