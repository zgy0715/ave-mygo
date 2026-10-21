package com.avemygo.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 管理端用户检索：用户名/昵称模糊匹配 + 分页 */
    @Query("""
            SELECT u FROM User u
            WHERE (:kw IS NULL
                   OR u.username LIKE CONCAT('%', :kw, '%')
                   OR u.nickname LIKE CONCAT('%', :kw, '%'))
            """)
    Page<User> search(@Param("kw") String kw, Pageable pageable);

    /** 批量统计部分用户的总消费与已支付订单数，供管理端列表展示（防 N+1） */
    @Query("""
            SELECT o.userId, COALESCE(SUM(o.totalAmount), 0), COUNT(o)
            FROM TicketOrder o
            WHERE o.userId IN :userIds AND o.status = 'PAID'
            GROUP BY o.userId
            """)
    List<Object[]> paidStats(@Param("userIds") Collection<Long> userIds);
}
