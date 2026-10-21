package com.avemygo.admin;

import com.avemygo.admin.dto.AdminUserDtos.StatusRequest;
import com.avemygo.admin.dto.AdminUserDtos.UserView;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端用户接口（仅 ADMIN）。 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService service;

    @GetMapping
    public ApiResponse<Page<UserView>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.search(keyword, page, pageSize));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserView> updateStatus(@PathVariable Long id,
                                              @Valid @RequestBody StatusRequest req,
                                              Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return ApiResponse.ok(service.updateStatus(adminId, id, req.status()));
    }
}
