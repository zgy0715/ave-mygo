package com.avemygo.song;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 专辑/单曲实体，对应表 album（docs/05 §3.3）。
 * 关联采用扁平 ID 派：只存 bandId，不装整个 Band 对象——查询行为可控、JSON 不嵌套。
 */
@Entity
@Table(name = "album")
@Getter
@Setter
@NoArgsConstructor
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 逻辑 FK → band.id */
    @Column(name = "band_id", nullable = false)
    private Short bandId;

    @Column(nullable = false, length = 100)
    private String name;

    /** SINGLE / EP / ALBUM / MINI_ALBUM */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Byte isDeleted = (byte) 0;
}
