package com.avemygo.song;

import com.avemygo.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 专辑接口：歌曲库筛选下拉框用。 */
@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumRepository albumRepository;

    @GetMapping
    public ApiResponse<List<Album>> list(@RequestParam(required = false) Short bandId) {
        List<Album> list = bandId == null
                ? albumRepository.findAll()
                : albumRepository.findByBandIdOrderByIdAsc(bandId);
        return ApiResponse.ok(list);
    }
}
