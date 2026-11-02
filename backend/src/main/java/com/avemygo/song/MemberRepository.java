package com.avemygo.song;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 成员数据访问。 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 按乐队取成员（sort 升序 = 主唱在前） */
    List<Member> findByBandIdAndIsDeletedOrderBySortAsc(Short bandId, Byte isDeleted);
}
