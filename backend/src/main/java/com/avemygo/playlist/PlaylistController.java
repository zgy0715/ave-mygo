package com.avemygo.playlist;

import com.avemygo.common.ApiResponse;
import com.avemygo.playlist.PlaylistDtos.AddSongRequest;
import com.avemygo.playlist.PlaylistDtos.CreateRequest;
import com.avemygo.playlist.PlaylistDtos.PlaylistDetail;
import com.avemygo.playlist.PlaylistDtos.PlaylistSummary;
import com.avemygo.playlist.PlaylistDtos.UpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 歌单接口：全部需要登录，userId 只从 JWT 取。 */
@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService service;

    @GetMapping
    public ApiResponse<List<PlaylistSummary>> list(Authentication auth) {
        return ApiResponse.ok(service.list(uid(auth)));
    }

    @PostMapping
    public ApiResponse<PlaylistSummary> create(@Valid @RequestBody CreateRequest req, Authentication auth) {
        return ApiResponse.ok(service.create(uid(auth), req.name(), req.description()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<PlaylistSummary> rename(@PathVariable Long id,
                                              @Valid @RequestBody UpdateRequest req,
                                              Authentication auth) {
        return ApiResponse.ok(service.rename(uid(auth), id, req.name(), req.description()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id, Authentication auth) {
        service.delete(uid(auth), id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlaylistDetail> detail(@PathVariable Long id, Authentication auth) {
        return ApiResponse.ok(service.detail(uid(auth), id));
    }

    @PostMapping("/{id}/songs")
    public ApiResponse<PlaylistDetail> addSong(@PathVariable Long id,
                                               @Valid @RequestBody AddSongRequest req,
                                               Authentication auth) {
        return ApiResponse.ok(service.addSong(uid(auth), id, req.songId()));
    }

    @DeleteMapping("/{id}/songs/{songId}")
    public ApiResponse<PlaylistDetail> removeSong(@PathVariable Long id, @PathVariable Long songId,
                                                  Authentication auth) {
        return ApiResponse.ok(service.removeSong(uid(auth), id, songId));
    }

    private Long uid(Authentication auth) {
        return Long.valueOf(auth.getName());
    }
}
