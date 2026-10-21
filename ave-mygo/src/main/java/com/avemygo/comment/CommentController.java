package com.avemygo.comment;

import com.avemygo.comment.CommentDtos.CommentView;
import com.avemygo.comment.CommentDtos.CreateRequest;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口：
 *  - GET  /api/v1/songs/{songId}/comments  公开（游客可看）
 *  - POST /api/v1/comments                需登录（路径不在白名单，靠 Security 默认拦截）
 *  - DELETE /api/v1/comments/{id}         需登录，本人或 ADMIN
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService service;

    @GetMapping("/songs/{songId}/comments")
    public ApiResponse<Page<CommentView>> list(@PathVariable Long songId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int pageSize,
                                               Authentication auth) {
        Long viewerId = auth == null ? null : parseId(auth);
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return ApiResponse.ok(service.list(songId, viewerId, isAdmin, page, pageSize));
    }

    @PostMapping("/comments")
    public ApiResponse<CommentView> create(@Valid @RequestBody CreateRequest req, Authentication auth) {
        return ApiResponse.ok(service.create(parseId(auth), req.songId(), req.content()));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        service.delete(parseId(auth), isAdmin, id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    private Long parseId(Authentication auth) {
        if (auth == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return Long.valueOf(auth.getName());
    }
}
