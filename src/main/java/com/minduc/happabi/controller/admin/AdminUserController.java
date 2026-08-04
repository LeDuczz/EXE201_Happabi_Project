package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.UserDTO;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.service.user.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin User Management", description = "User management for administrators")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final IUserService userService;

    @GetMapping
    @Operation(summary = "Get list of all users with optional search")
    public ResponseEntity<BaseResponse<Page<UserDTO>>> getAllUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatusFilter status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                BaseResponse.ok("Get all users success", userService.getAllUsers(query, role, status == null ? null : status.active, pageable)));
    }

    @PostMapping("/{userId}/toggle-status")
    @Operation(summary = "Toggle user active status")
    public ResponseEntity<BaseResponse<Void>> toggleUserStatus(@PathVariable UUID userId) {
        userService.toggleUserStatus(userId);
        return ResponseEntity.ok(
                BaseResponse.ok("Toggle user status success", null));
    }

    public enum UserStatusFilter {
        ACTIVE(true),
        LOCKED(false);

        private final Boolean active;

        UserStatusFilter(Boolean active) {
            this.active = active;
        }
    }
}
