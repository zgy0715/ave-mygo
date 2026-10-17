package com.avemygo.song;

import org.springframework.data.jpa.repository.JpaRepository;

/** 乐队数据访问。列表接口只需全查（band 总共就几行），无自定义方法。 */
public interface BandRepository extends JpaRepository<Band, Short> {
}
