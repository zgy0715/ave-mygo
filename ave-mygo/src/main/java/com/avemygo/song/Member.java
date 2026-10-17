package com.avemygo.song;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** 乐队成员实体，对应表 member（docs/05 §3.4）。 */
@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 逻辑 FK → band.id */
    @Column(name = "band_id", nullable = false)
    private Short bandId;

    @Column(name = "name_ja", nullable = false, length = 50)
    private String nameJa;

    @Column(name = "name_romaji", nullable = false, length = 50)
    private String nameRomaji;

    /** 声优 */
    @Column(nullable = false, length = 50)
    private String cv;

    /** Vo. / Gt. / Ba. / Dr. / Key. / Dj. */
    @Column(nullable = false, length = 20)
    private String position;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String intro;

    @Column(nullable = false)
    private Integer sort = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Byte isDeleted = (byte) 0;
}
