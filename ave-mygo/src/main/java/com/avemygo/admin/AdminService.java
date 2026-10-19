package com.avemygo.admin;

import com.avemygo.admin.dto.AdminDtos.AdminSongView;
import com.avemygo.admin.dto.AdminDtos.SongPatch;
import com.avemygo.admin.dto.AdminDtos.SongUpsert;
import com.avemygo.common.BizException;
import com.avemygo.song.Band;
import com.avemygo.song.BandRepository;
import com.avemygo.song.Song;
import com.avemygo.song.SongRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端业务层：歌曲 CRUD（docs/05 M4.8）。
 * 与前台 SongService 分离——管理端可见下架歌曲、可写音源/MV 外链。
 * 红线沿用：audio_url/mv_url 只存官方外链。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final SongRepository songRepository;
    private final BandRepository bandRepository;

    /** 管理视图：全量字段 + 乐队名（两次查询组装，页大小有限无 N+1 之虞） */
    @Transactional(readOnly = true)
    public Page<AdminSongView> songs(Short bandId, String keyword, int page, int pageSize) {
        Page<Song> songs = songRepository.search(bandId, (keyword == null || keyword.isBlank()) ? null : keyword,
                null, PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(pageSize, 1), 50),
                        Sort.by(Sort.Direction.ASC, "id")));
        Map<Short, Band> bands = bandRepository.findAllById(
                        songs.map(Song::getBandId).stream().distinct().toList())
                .stream().collect(Collectors.toMap(Band::getId, Function.identity()));
        return songs.map(s -> toView(s, bands.get(s.getBandId())));
    }

    @Transactional
    public AdminSongView create(SongUpsert req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new BizException(40001, "歌曲名不能为空");
        }
        requireBand(req.bandId());
        Song song = new Song();
        song.setTitle(req.title().trim());
        song.setBandId(req.bandId());
        applyPatch(song, req.albumId(), req.trackNo(), req.releaseDate(), req.duration(),
                req.coverUrl(), req.audioUrl(), req.mvUrl(), req.mvPlatform(), req.tags(), req.status());
        song.setStatus(req.status() == null ? (byte) 1 : req.status());
        return toView(songRepository.save(song), bandOf(req.bandId()));
    }

    @Transactional
    public AdminSongView update(Long id, SongPatch req) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new BizException(40004, "歌曲不存在"));
        applyPatch(song, null, null, null, null, null, null, null, null, null, null);
        if (req.title() != null && !req.title().isBlank()) song.setTitle(req.title().trim());
        applyPatch(song, req.albumId(), req.trackNo(), req.releaseDate(), req.duration(),
                req.coverUrl(), req.audioUrl(), req.mvUrl(), req.mvPlatform(), req.tags(), req.status());
        return toView(songRepository.save(song), bandOf(song.getBandId()));
    }

    /** 软删除：is_deleted=1，前台与后台列表都不可见 */
    @Transactional
    public void delete(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new BizException(40004, "歌曲不存在"));
        song.setIsDeleted((byte) 1);
        songRepository.save(song);
    }

    // ---- 私有工具 ----

    private void applyPatch(Song song, Long albumId, Integer trackNo, LocalDate releaseDate,
                            Integer duration, String coverUrl, String audioUrl,
                            String mvUrl, String mvPlatform, String tags, Byte status) {
        if (albumId != null) song.setAlbumId(albumId);
        if (trackNo != null) song.setTrackNo(trackNo);
        if (releaseDate != null) song.setReleaseDate(releaseDate);
        if (duration != null) song.setDuration(duration);
        if (coverUrl != null) song.setCoverUrl(blankToNull(coverUrl));
        if (audioUrl != null) song.setAudioUrl(blankToNull(audioUrl));
        if (mvUrl != null) song.setMvUrl(blankToNull(mvUrl));
        if (mvPlatform != null) song.setMvPlatform(blankToNull(mvPlatform));
        if (tags != null) song.setTags(blankToNull(tags));
        if (status != null) song.setStatus(status);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void requireBand(Short bandId) {
        if (bandId == null || !bandRepository.existsById(bandId)) {
            throw new BizException(40001, "乐队不存在");
        }
    }

    private Band bandOf(Short bandId) {
        return bandRepository.findById(bandId).orElse(null);
    }

    private AdminSongView toView(Song s, Band band) {
        return new AdminSongView(s.getId(), s.getTitle(), s.getBandId(),
                band != null ? band.getName() : null,
                s.getAlbumId(), s.getTrackNo(), s.getReleaseDate(), s.getDuration(),
                s.getCoverUrl(), s.getAudioUrl(), s.getMvUrl(), s.getMvPlatform(),
                s.getTags(), s.getStatus());
    }
}
