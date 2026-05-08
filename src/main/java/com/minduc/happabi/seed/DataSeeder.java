package com.minduc.happabi.seed;

import com.minduc.happabi.entity.Permission;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.RolePermission;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.PermissionRepository;
import com.minduc.happabi.repository.RolePermissionRepository;
import com.minduc.happabi.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional
    public void seedRolesAndPermissions() {
        List<Role> roles = roleRepository.findAll();
        if (roles.isEmpty()) {
            log.info("Seeding roles...");
            roles = roleRepository.saveAll(List.of(
                    Role.builder().roleName(UserRole.MOTHER).description("Mẹ bỉm sữa").isSystem(true).build(),
                    Role.builder().roleName(UserRole.NURSE).description("Điều dưỡng / Nữ hộ sinh").isSystem(true).build(),
                    Role.builder().roleName(UserRole.DOCTOR).description("Bác sĩ / Người xét duyệt").isSystem(true).build(),
                    Role.builder().roleName(UserRole.ADMIN).description("Quản trị viên").isSystem(true).build()
            ));
            log.info("Roles seeded successfully.");
        }

        List<Permission> permissions = permissionRepository.findAll();
        if (permissions.isEmpty()) {
            log.info("Seeding permissions...");
            permissions = permissionRepository.saveAll(List.of(
                    Permission.builder().permissionName("USER:READ").resource("USER").action("READ").description("Xem thông tin tài khoản").build(),
                    Permission.builder().permissionName("USER:UPDATE").resource("USER").action("UPDATE").description("Cập nhật thông tin tài khoản").build(),
                    Permission.builder().permissionName("USER:MANAGE").resource("USER").action("MANAGE").description("Quản lý toàn bộ người dùng (admin)").build(),
                    Permission.builder().permissionName("NURSE:READ").resource("NURSE").action("READ").description("Xem danh sách điều dưỡng").build(),
                    Permission.builder().permissionName("NURSE:UPDATE").resource("NURSE").action("UPDATE").description("Cập nhật hồ sơ điều dưỡng của mình").build(),
                    Permission.builder().permissionName("NURSE:APPROVE").resource("NURSE").action("APPROVE").description("Xét duyệt hồ sơ điều dưỡng").build(),
                    Permission.builder().permissionName("NURSE:MANAGE").resource("NURSE").action("MANAGE").description("Quản lý toàn bộ điều dưỡng (admin)").build(),
                    Permission.builder().permissionName("ROLE:MANAGE").resource("ROLE").action("MANAGE").description("Quản lý roles & permissions").build(),
                    Permission.builder().permissionName("ADMIN:MANAGE").resource("ADMIN").action("MANAGE").description("Toàn quyền hệ thống (admin)").build()
            ));
            log.info("Permissions seeded successfully.");
        }

        if (rolePermissionRepository.count() == 0 && !roles.isEmpty() && !permissions.isEmpty()) {
            boolean hasChanges = false;

            Role admin = roles.stream().filter(r -> r.getRoleName() == UserRole.ADMIN).findFirst().orElseThrow();
            Role doctor = roles.stream().filter(r -> r.getRoleName() == UserRole.DOCTOR).findFirst().orElseThrow();
            Role nurse = roles.stream().filter(r -> r.getRoleName() == UserRole.NURSE).findFirst().orElseThrow();
            Role mother = roles.stream().filter(r -> r.getRoleName() == UserRole.MOTHER).findFirst().orElseThrow();

            if (admin.getRolePermissions().isEmpty()) {
                hasChanges = true;
                log.info("Seeding role permissions mapping...");
                List<RolePermission> rolePermissions = new ArrayList<>();
                for (Permission p : permissions) {
                    // Admin full quyền
                    rolePermissions.add(RolePermission.builder().role(admin).permission(p).build());

                    // Nurse: User (Read, Update), Nurse (Read, Update)
                    if (p.getPermissionName().equals("USER:READ") || p.getPermissionName().equals("USER:UPDATE") ||
                        p.getPermissionName().equals("NURSE:READ") || p.getPermissionName().equals("NURSE:UPDATE")) {
                        rolePermissions.add(RolePermission.builder().role(nurse).permission(p).build());
                    }

                    // Doctor: User (Read, Update), Nurse (Read, Approve)
                    if (p.getPermissionName().equals("USER:READ") || p.getPermissionName().equals("USER:UPDATE") ||
                        p.getPermissionName().equals("NURSE:READ") || p.getPermissionName().equals("NURSE:APPROVE")) {
                        rolePermissions.add(RolePermission.builder().role(doctor).permission(p).build());
                    }

                    // Mother: User (Read, Update), Nurse (Read)
                    if (p.getPermissionName().equals("USER:READ") || p.getPermissionName().equals("USER:UPDATE") ||
                        p.getPermissionName().equals("NURSE:READ")) {
                        rolePermissions.add(RolePermission.builder().role(mother).permission(p).build());
                    }
                }
                rolePermissionRepository.saveAll(rolePermissions);
            }

            if (hasChanges) {
                log.info("Role permissions mapping seeded successfully.");
            }
        }
    }
}
