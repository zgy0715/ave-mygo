package com.avemygo.favorite;

import com.avemygo.common.BizException;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.favorite.FavoriteDtos.FavoriteItem;
import com.avemygo.song.Band;
import com.avemygo.song.BandRepository;
import com.avemygo.song.Song;
import com.avemygo.song.SongRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收藏业务：多态目标（歌曲/演出）。
 * 设计要点：
 *  - 重复收藏幂等（唯一索引 + 先查后插），前端可放心做乐观更新
 *  - 取消收藏幂等（不存在也返回成功），避免双击报错
 *  - 列表用批量 IN 查询取标题/封面，不做逐条查询（防 N+1）
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final Set<String> TYPES = Set.of("SONG", "EVENT");

    private final FavoriteRepository favoriteRepository;
    private final SongRepository songRepository;
    private final EventRepository eventRepository;
    private final BandRepository bandRepository;

    @Transactional(readOnly = true)
    public List<FavoriteItem> list(Long userId, String targetType) {
        String type = normalize(targetType);
        List<Favorite> favorites = favoriteRepository
                .findByUserIdAndTargetTypeOrderByCreatedAtDesc(userId, type);
        if (favorites.isEmpty()) return List.of();

        if ("SONG".equals(type)) {
            Map<Long, Song> songs = songRepository.findAllById(
                            favorites.stream().map(Favorite::getTargetId).toList())
                    .stream().collect(Collectors.toMap(Song::getId, Function.identity()));
            Map<Short, String> bandNames = bandRepository.findAllById(
                            songs.values().stream().map(Song::getBandId).distinct().toList())
                    .stream().collect(Collectors.toMap(Band::getId, Band::getName));
            return favorites.stream()
                    .filter(f -> songs.containsKey(f.getTargetId()))   // 歌被下架/软删则跳过
                    .map(f -> {
                        Song s = songs.get(f.getTargetId());
                        return new FavoriteItem("SONG", s.getId(), s.getTitle(), s.getCoverUrl(),
                                bandNames.get(s.getBandId()), f.getCreatedAt());
                    })
                    .toList();
        }

        Map<Long, LiveEvent> events = eventRepository.findAllById(
                        favorites.stream().map(Favorite::getTargetId).toList())
                .stream().collect(Collectors.toMap(LiveEvent::getId, Function.identity()));
        return favorites.stream()
                .filter(f -> events.containsKey(f.getTargetId()))
                .map(f -> {
                    LiveEvent e = events.get(f.getTargetId());
                    return new FavoriteItem("EVENT", e.getId(), e.getTitle(), e.getPosterUrl(),
                            e.getVenue(), f.getCreatedAt());
                })
                .toList();
    }

    /** 添加收藏：幂等。目标不存在 → 40401；类型非法 → 40001。 */
    @Transactional
    public FavoriteItem add(Long userId, String targetType, Long targetId) {
        String type = normalize(targetType);
        if ("SONG".equals(type)) {
            Song song = songRepository.findById(targetId)
                    .orElseThrow(() -> new BizException(40401, "歌曲不存在或已下架"));
            favoriteRepository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
                    .orElseGet(() -> {
                        Favorite f = new Favorite();
                        f.setUserId(userId);
                        f.setTargetType(type);
                        f.setTargetId(targetId);
                        return favoriteRepository.save(f);
                    });
            return new FavoriteItem("SONG", song.getId(), song.getTitle(), song.getCoverUrl(),
                    bandRepository.findById(song.getBandId()).map(Band::getName).orElse(null), null);
        }

        LiveEvent event = eventRepository.findById(targetId)
                .orElseThrow(() -> new BizException(40401, "演出不存在"));
        if ("DRAFT".equals(event.getStatus())) {
            throw new BizException(40401, "演出不存在");     // 草稿对用户不可见，也不允许收藏
        }
        favoriteRepository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
                .orElseGet(() -> {
                    Favorite f = new Favorite();
                    f.setUserId(userId);
                    f.setTargetType(type);
                    f.setTargetId(targetId);
                    return favoriteRepository.save(f);
                });
        return new FavoriteItem("EVENT", event.getId(), event.getTitle(), event.getPosterUrl(),
                event.getVenue(), null);
    }

    /** 取消收藏：幂等（不存在也返回 true）。 */
    @Transactional
    public void remove(Long userId, String targetType, Long targetId) {
        String type = normalize(targetType);
        favoriteRepository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
                .ifPresent(favoriteRepository::delete);
    }

    private String normalize(String targetType) {
        String type = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new BizException(40001, "收藏类型只能是 SONG 或 EVENT");
        }
        return type;
    }
}
