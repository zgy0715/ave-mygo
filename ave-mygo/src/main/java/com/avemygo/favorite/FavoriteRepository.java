package com.avemygo.favorite;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 收藏数据访问：全部按 userId 隔离，不存在跨用户查询入口。 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserIdAndTargetTypeOrderByCreatedAtDesc(Long userId, String targetType);

    Optional<Favorite> findByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    long countByUserIdAndTargetType(Long userId, String targetType);
}
