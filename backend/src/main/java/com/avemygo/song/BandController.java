package com.avemygo.song;

import com.avemygo.common.ApiResponse;
import com.avemygo.common.BizException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 乐队接口（公开）：筛选下拉 + 乐队主页（成员/专辑）。 */
@RestController
@RequestMapping("/api/v1/bands")
@RequiredArgsConstructor
public class BandController {

    private final BandRepository bandRepository;
    private final MemberRepository memberRepository;
    private final AlbumRepository albumRepository;

    @GetMapping
    public ApiResponse<List<Band>> list() {
        return ApiResponse.ok(bandRepository.findAll());
    }

    /** 乐队详情：基本信息 + 成员（按 sort 排序）+ 专辑 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Short id) {
        Band band = bandRepository.findById(id)
                .orElseThrow(() -> new BizException(40401, "乐队不存在"));
        List<Member> members = memberRepository
                .findByBandIdAndIsDeletedOrderBySortAsc(id, (byte) 0);
        List<Album> albums = albumRepository.findByBandIdOrderByIdAsc(id);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("band", band);
        data.put("members", members);
        data.put("albums", albums);
        return ApiResponse.ok(data);
    }
}
