package com.avemygo.song;

import com.avemygo.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 乐队接口（公开）：首页筛选下拉用。 */
@RestController
@RequestMapping("/api/v1/bands")
@RequiredArgsConstructor
public class BandController {

    private final BandRepository bandRepository;

    @GetMapping
    public ApiResponse<List<Band>> list() {
        return ApiResponse.ok(bandRepository.findAll());
    }
}
