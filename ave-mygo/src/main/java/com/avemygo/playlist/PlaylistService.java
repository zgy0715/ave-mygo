package com.avemygo.playlist;

import com.avemygo.common.BizException;
import com.avemygo.playlist.PlaylistDtos.PlaylistDetail;
import com.avemygo.playlist.PlaylistDtos.PlaylistSummary;
import com.avemygo.song.Song;
import com.avemygo.song.SongRepository;
import com.avemygo.song.SongService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 歌单业务：所有操作先做归属校验（找不到 = 别人的歌单 = 40401，不泄露存在性）。
 * 添加曲目幂等（同一首歌重复加入不报错）；取消曲目幂等。
 */
@Service
@RequiredArgsConstructor
public class PlaylistService {

    private static final int MAX_PLAYLISTS_PER_USER = 50;

    private final PlaylistRepository playlistRepository;
    private final PlaylistItemRepository itemRepository;
    private final SongRepository songRepository;
    private final SongService songService;

    @Transactional(readOnly = true)
    public List<PlaylistSummary> list(Long userId) {
        List<Playlist> playlists = playlistRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (playlists.isEmpty()) return List.of();

        // 曲目数：一条分组 SQL
        Map<Long, Long> counts = itemRepository.countGrouped(
                        playlists.stream().map(Playlist::getId).toList())
                .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        // 封面：取每个歌单 sort 最小的一首歌的封面
        Map<Long, Long> firstSongByPlaylist = new HashMap<>();
        for (Playlist p : playlists) {
            List<PlaylistItem> items = itemRepository.findByPlaylistIdOrderBySortAscIdAsc(p.getId());
            if (!items.isEmpty()) firstSongByPlaylist.put(p.getId(), items.get(0).getSongId());
        }
        // 注意：不能用 Map.of()（不可变 Map 的 get(null) 会抛 NPE），空歌单没有封面键
        Map<Long, String> covers = new HashMap<>();
        if (!firstSongByPlaylist.isEmpty()) {
            songRepository.findAllById(new LinkedHashSet<>(firstSongByPlaylist.values()))
                    .forEach(s -> covers.put(s.getId(), s.getCoverUrl() == null ? "" : s.getCoverUrl()));
        }

        return playlists.stream()
                .map(p -> new PlaylistSummary(p.getId(), p.getName(), p.getDescription(),
                        counts.getOrDefault(p.getId(), 0L).intValue(),
                        coverOf(covers, firstSongByPlaylist.get(p.getId())), p.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public PlaylistSummary create(Long userId, String name, String description) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new BizException(40001, "歌单名不能为空");
        if (playlistRepository.countByUserId(userId) >= MAX_PLAYLISTS_PER_USER) {
            throw new BizException(40001, "歌单数量已达上限（" + MAX_PLAYLISTS_PER_USER + " 个）");
        }
        if (playlistRepository.existsByUserIdAndName(userId, trimmed)) {
            throw new BizException(40001, "已有同名歌单");
        }
        Playlist p = new Playlist();
        p.setUserId(userId);
        p.setName(trimmed);
        p.setDescription(description == null || description.isBlank() ? null : description.trim());
        p = playlistRepository.save(p);
        return new PlaylistSummary(p.getId(), p.getName(), p.getDescription(), 0, null, p.getUpdatedAt());
    }

    @Transactional
    public PlaylistSummary rename(Long userId, Long playlistId, String name, String description) {
        Playlist p = owned(userId, playlistId);
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            if (!trimmed.equals(p.getName()) && playlistRepository.existsByUserIdAndName(userId, trimmed)) {
                throw new BizException(40001, "已有同名歌单");
            }
            p.setName(trimmed);
        }
        if (description != null) {
            p.setDescription(description.isBlank() ? null : description.trim());
        }
        p = playlistRepository.save(p);
        return new PlaylistSummary(p.getId(), p.getName(), p.getDescription(),
                (int) itemRepository.countByPlaylistId(playlistId), null, p.getUpdatedAt());
    }

    @Transactional
    public void delete(Long userId, Long playlistId) {
        Playlist p = owned(userId, playlistId);
        itemRepository.deleteByPlaylistId(playlistId);
        playlistRepository.delete(p);
    }

    @Transactional(readOnly = true)
    public PlaylistDetail detail(Long userId, Long playlistId) {
        Playlist p = owned(userId, playlistId);
        List<Long> songIds = itemRepository.findByPlaylistIdOrderBySortAscIdAsc(playlistId)
                .stream().map(PlaylistItem::getSongId).toList();
        return new PlaylistDetail(p.getId(), p.getName(), p.getDescription(),
                songService.itemsByIds(songIds), p.getUpdatedAt());
    }

    /** 加入歌单：幂等；歌曲必须存在且未下架 */
    @Transactional
    public PlaylistDetail addSong(Long userId, Long playlistId, Long songId) {
        owned(userId, playlistId);
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new BizException(40401, "歌曲不存在或已下架"));
        if (song.getStatus() == null || song.getStatus() != 1) {
            throw new BizException(40401, "歌曲不存在或已下架");
        }
        if (itemRepository.findByPlaylistIdAndSongId(playlistId, songId).isEmpty()) {
            Integer max = itemRepository.maxSort(playlistId);
            PlaylistItem item = new PlaylistItem();
            item.setPlaylistId(playlistId);
            item.setSongId(songId);
            item.setSort(max == null ? 0 : max + 1);
            itemRepository.save(item);
        }
        return detail(userId, playlistId);
    }

    /** 移出歌单：幂等（不存在也成功） */
    @Transactional
    public PlaylistDetail removeSong(Long userId, Long playlistId, Long songId) {
        owned(userId, playlistId);
        itemRepository.deleteByPlaylistIdAndSongId(playlistId, songId);
        return detail(userId, playlistId);
    }

    private String coverOf(Map<Long, String> covers, Long songId) {
        if (songId == null) return null;
        String cover = covers.get(songId);
        return (cover == null || cover.isEmpty()) ? null : cover;
    }

    private Playlist owned(Long userId, Long playlistId) {
        return playlistRepository.findByIdAndUserId(playlistId, userId)
                .orElseThrow(() -> new BizException(40401, "歌单不存在"));
    }
}
