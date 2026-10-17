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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 乐队实体，对应表 band（docs/05 §3.2）。
 * 表已由 V1__init.sql 建好并插入 MyGO!!!! / Ave Mujica 两条种子数据。
 */
@Entity
@Table(name = "band")
@Getter
@Setter
@NoArgsConstructor
public class Band {

    /** SMALLINT UNSIGNED：实测 validate 要求 Short（Integer 会报 INTEGER 不匹配） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50, unique = true)
    private String slug;

    /** 表中为 TEXT：用 LONGVARCHAR 声明期望类型，避免 validate 报 varchar 不匹配 */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "logo_url")
    private String logoUrl;
}
