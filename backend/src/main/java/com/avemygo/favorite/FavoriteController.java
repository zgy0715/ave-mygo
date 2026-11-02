package com.avemygo.favorite;

import com.avemygo.common.ApiResponse;
import com.avemygo.favorite.FavoriteDtos.AddRequest;
import com.avemygo.favorite.FavoriteDtos.FavoriteItem;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口 —— 全部需要登录（/favorites 不在公开白名单里）。
 * userId 一律从 JWT 盖章的 Authentication 取，不接受请求参数传入。
 */
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ApiResponse<List<FavoriteItem>> list(@RequestParam(defaultValue = "SONG") String targetType,
                                                Authentication authentication) {
        return ApiResponse.ok(favoriteService.list(userId(authentication), targetType));
    }

    @PostMapping
    public ApiResponse<FavoriteItem> add(@Valid @RequestBody AddRequest req,
                                        Authentication authentication) {
        return ApiResponse.ok(favoriteService.add(userId(authentication), req.targetType(), req.targetId()));
    }

    @DeleteMapping("/{targetType}/{targetId}")
    public ApiResponse<Map<String, Boolean>> remove(@PathVariable String targetType,
                                                    @PathVariable Long targetId,
                                                    Authentication authentication) {
        favoriteService.remove(userId(authentication), targetType, targetId);
        return ApiResponse.ok(Map.of("removed", true));
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());   // JWT subject = userId
    }
}
