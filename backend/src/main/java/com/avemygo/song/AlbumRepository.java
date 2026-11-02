package com.avemygo.song;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 专辑数据访问。给歌曲筛选下拉框和详情页用。 */
public interface AlbumRepository extends JpaRepository<Album, Long> {

    /** 按乐队查专辑（前端筛选下拉用） */
    List<Album> findByBandIdOrderByIdAsc(Short bandId);
}
