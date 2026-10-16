package com.avemygo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 user（docs/05 §3.1）。
 * 表的一行 = 本类的一个对象；框架负责双向翻译。
 */
@Entity
@Table(name = "user")
@SQLRestriction("is_deleted = 0")   // 软删除：所有查询自动过滤已删除用户
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, unique = true)
    private String username;

    /** BCrypt 密文，绝不存明文密码 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String nickname;

    /** USER / ADMIN（docs/05：枚举值存大写英文常量） */
    @Column(nullable = false, length = 20)
    private String role = "USER";

    /** 1 正常 / 0 封禁 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted = 0;
}
