package com.avemygo.song;

import com.avemygo.common.ApiResponse;
import com.avemygo.song.dto.SongDtos.SongDetail;
import com.avemygo.song.dto.SongDtos.SongItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 歌曲库接口（公开，游客可浏览）。
 * GET /api/v1/songs       列表：bandId/keyword/tag/sort/page/pageSize 全部可选
 * GET /api/v1/songs/{id}  详情
 */
@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    @GetMapping
    public ApiResponse<Page<SongItem>> list(
            @RequestParam(required = false) Short bandId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "release_desc") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize) {
        return ApiResponse.ok(songService.list(bandId, keyword, tag, sort, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<SongDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(songService.detail(id));
    }
}
