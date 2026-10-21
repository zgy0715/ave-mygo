package com.avemygo.admin;

import com.avemygo.admin.dto.AdminSetlistDtos.Item;
import com.avemygo.common.BizException;
import com.avemygo.event.EventService;
import com.avemygo.event.LiveSessionRepository;
import com.avemygo.event.SetlistItem;
import com.avemygo.event.SetlistItemRepository;
import com.avemygo.event.dto.EventDtos.SetlistItemView;
import com.avemygo.song.SongRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端歌单录入：PUT 全量替换（先删后插，单事务）。
 *
 * 校验顺序（错误信息越具体越好，前端能直接展示）：
 *  1. 场次存在
 *  2. 曲目不重复（同一首歌不能出现两次）
 *  3. 曲序不重复（重复会让排序不确定）
 *  4. 歌曲全部存在且未下架
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSetlistService {

    private final LiveSessionRepository sessionRepository;
    private final SetlistItemRepository itemRepository;
    private final SongRepository songRepository;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public List<SetlistItemView> get(Long sessionId) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(40401, "场次不存在"));
        return eventService.setlist(sessionId);
    }

    @Transactional
    public List<SetlistItemView> replace(Long sessionId, List<Item> items) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(40401, "场次不存在"));

        Set<Long> songIds = new HashSet<>();
        Set<Integer> orderNos = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (!songIds.add(it.songId())) {
                throw new BizException(40001, "同一首歌不能重复加入歌单");
            }
            int orderNo = it.orderNo() == null ? i + 1 : it.orderNo();
            if (!orderNos.add(orderNo)) {
                throw new BizException(40001, "曲序不能重复：" + orderNo);
            }
        }

        long existing = songRepository.findAllById(songIds).stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                .count();
        if (existing != songIds.size()) {
            throw new BizException(40401, "存在不存在或已下架的歌曲");
        }

        itemRepository.deleteBySessionIdQuery(sessionId);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            SetlistItem entity = new SetlistItem();
            entity.setSessionId(sessionId);
            entity.setSongId(it.songId());
            entity.setOrderNo(it.orderNo() == null ? i + 1 : it.orderNo());
            entity.setIsEncore(Boolean.TRUE.equals(it.encore()) ? (byte) 1 : (byte) 0);
            itemRepository.save(entity);
        }
        log.info("管理端保存歌单 sessionId={} 曲目数={}", sessionId, items.size());
        return eventService.setlist(sessionId);
    }
}
