package com.avemygo.search;

import com.avemygo.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 全局搜索（公开接口，游客可用）：顶栏搜索框 + 下拉结果。 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService service;

    @GetMapping
    public ApiResponse<SearchResult> search(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(service.search(keyword, limit));
    }
}
