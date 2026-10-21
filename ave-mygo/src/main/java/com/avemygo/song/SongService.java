package com.avemygo.song;

import com.avemygo.common.BizException;
import com.avemygo.song.dto.SongDtos.SongDetail;
import com.avemygo.song.dto.SongDtos.SongItem;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 歌曲业务层：筛选/分页/排序的编排 + 实体到 DTO 的翻译。
 */
@Service
@RequiredArgsConstructor
public class SongService {

    private final SongRepository songRepository;
    private final BandRepository bandRepository;
    private final AlbumRepository albumRepository;

    /** 歌曲列表：可选筛选 + 白名单排序 + 分页（页码从 1 起，单页上限 50） */
    public Page<SongItem> list(Short bandId, String keyword, String tag, String sort, int page, int pageSize) {
        Sort sortSpec = switch (sort == null ? "release_desc" : sort) {
            case "release_asc" -> Sort.by(Sort.Direction.ASC, "releaseDate");
            case "title_asc" -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "releaseDate");   // 默认最新发行在前
        };

        Page<Song> result = songRepository.search(
                bandId, blankToNull(keyword), blankToNull(tag),
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(pageSize, 1), 50), sortSpec));

        // 小表整表缓存成 id->name 映射，避免逐行查乐队/专辑名（防 N+1）
        Map<Short, String> bandNames = toMap(bandRepository.findAll(), Band::getId, Band::getName);
        Map<Long, String> albumNames = toMap(albumRepository.findAll(), Album::getId, Album::getName);

        return result.map(s -> new SongItem(
                s.getId(), s.getTitle(), s.getBandId(),
                bandNames.get(s.getBandId()), s.getAlbumId(),
                s.getAlbumId() == null ? null : albumNames.get(s.getAlbumId()),
                s.getDuration(), s.getCoverUrl(), s.getAudioUrl(), s.getStreamUrl(),
                s.getMvUrl(), s.getMvPlatform(), s.getTags(), s.getReleaseDate()));
    }

    /**
     * 按给定 id 顺序返回歌曲列表项（歌单详情用）。
     * 不存在的 id 自动跳过（可能已下架/软删），保持调用方顺序。
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<SongItem> itemsByIds(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) return java.util.List.of();
        Map<Long, Song> byId = songRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Song::getId, java.util.function.Function.identity()));
        Map<Short, String> bandNames = toMap(bandRepository.findAll(), Band::getId, Band::getName);
        Map<Long, String> albumNames = toMap(albumRepository.findAll(), Album::getId, Album::getName);
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(s -> new SongItem(
                        s.getId(), s.getTitle(), s.getBandId(), bandNames.get(s.getBandId()),
                        s.getAlbumId(), s.getAlbumId() == null ? null : albumNames.get(s.getAlbumId()),
                        s.getDuration(), s.getCoverUrl(), s.getAudioUrl(), s.getStreamUrl(),
                        s.getMvUrl(), s.getMvPlatform(), s.getTags(), s.getReleaseDate()))
                .toList();
    }

    /** 歌曲详情：下架/不存在一律 40401，不向前台区分 */
    public SongDetail detail(Long id) {
        Song s = songRepository.findById(id)
                .orElseThrow(() -> new BizException(40401, "歌曲不存在"));
        if (s.getStatus() == 0) {
            throw new BizException(40401, "歌曲不存在");
        }

        String bandName = bandRepository.findById(s.getBandId()).map(Band::getName).orElse(null);
        var album = s.getAlbumId() == null ? null : albumRepository.findById(s.getAlbumId()).orElse(null);
        boolean mvEmbedReady = StringUtils.hasText(s.getMvUrl()) && StringUtils.hasText(s.getMvPlatform());

        return new SongDetail(
                s.getId(), s.getTitle(), s.getBandId(), bandName,
                s.getAlbumId(),
                album == null ? null : album.getName(),
                album == null ? null : album.getType(),
                s.getTrackNo(), s.getReleaseDate(), s.getDuration(),
                s.getCoverUrl(), s.getAudioUrl(), s.getStreamUrl(), s.getMvUrl(), s.getMvPlatform(),
                mvEmbedReady, s.getTags());
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static <T, K, V> Map<K, V> toMap(Iterable<T> list, Function<T, K> key, Function<T, V> value) {
        Map<K, V> map = new HashMap<>();
        list.forEach(t -> map.put(key.apply(t), value.apply(t)));
        return map;
    }
}
