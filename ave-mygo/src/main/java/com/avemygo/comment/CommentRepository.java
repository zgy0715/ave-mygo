package com.avemygo.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 评论数据访问：按歌曲倒序分页（软删除由实体 @SQLRestriction 过滤）。 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findBySongIdOrderByIdDesc(Long songId, Pageable pageable);

    long countBySongId(Long songId);
}
