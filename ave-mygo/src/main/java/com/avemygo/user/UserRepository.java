package com.avemygo.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户表数据访问层。
 * 继承 JpaRepository 即自动获得 save/findById/findAll/delete 等方法；
 * 方法名按 Spring Data 规则命名即可自动生成 SQL，无需手写。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按用户名查用户 —— 自动生成 SELECT * FROM user WHERE username = ? */
    Optional<User> findByUsername(String username);

    /** 用户名是否已存在（注册查重用，比查整行更快） */
    boolean existsByUsername(String username);
}
