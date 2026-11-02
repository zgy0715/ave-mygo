package com.avemygo.search;

import com.avemygo.common.BizException;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.event.LiveSessionRepository;
import com.avemygo.search.SearchResult.EventBrief;
import com.avemygo.song.SongService;
import com.avemygo.song.dto.SongDtos.SongItem;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全局搜索：歌曲（标题/乐队关键词走现有索引查询）+ 演出（标题匹配，排除草稿）。
 * 两组各取 limit 条，前端下拉用；"查看全部"再跳对应列表页。
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_LIMIT = 20;

    private final SongService songService;
    private final EventRepository eventRepository;
    private final LiveSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public SearchResult search(String keyword, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            throw new BizException(40001, "搜索关键词不能为空");
        }
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<SongItem> songs = songService
                .list(null, kw, null, "release_desc", 1, size)
                .getContent();

        List<LiveEvent> events = eventRepository.searchByTitle(kw, PageRequest.of(0, size));
        Map<Long, LocalDateTime> nextStarts = new HashMap<>();
        if (!events.isEmpty()) {
            for (Object[] row : sessionRepository.nextStartByEvent(
                    events.stream().map(LiveEvent::getId).toList(), LocalDateTime.now())) {
                nextStarts.put(((Number) row[0]).longValue(), (LocalDateTime) row[1]);
            }
        }
        List<EventBrief> briefs = events.stream()
                .map(e -> new EventBrief(e.getId(), e.getTitle(), e.getVenue(), e.getStatus(),
                        e.getPosterUrl(), nextStarts.get(e.getId())))
                .toList();

        return new SearchResult(songs, briefs);
    }
}
