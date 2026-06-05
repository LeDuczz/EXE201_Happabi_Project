package com.minduc.happabi.seed;

import com.minduc.happabi.entity.Permission;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.RolePermission;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserIdentityProvider;
import com.minduc.happabi.entity.UserRoleAssignment;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.PermissionRepository;
import com.minduc.happabi.repository.RolePermissionRepository;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.ServiceOfferingRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.repository.UserRoleAssignmentRepository;
import com.minduc.happabi.integration.cognito.CognitoService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserIdentityProviderRepository userIdentityProviderRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final CognitoService cognitoService;

    @Value("${app.seed.admin.enabled:true}")
    private boolean adminSeedEnabled;

    @Value("${app.seed.admin.phone:}")
    private String adminPhone;

    @Value("${app.seed.admin.password:}")
    private String adminPassword;

    @Value("${app.seed.admin.full-name:System Administrator}")
    private String adminFullName;

    @Value("${app.seed.admin.email:}")
    private String adminEmail;

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
                    Permission.builder().permissionName("DOCTOR:CREATE").resource("DOCTOR").action("CREATE").description("Tạo tài khoản bác sĩ").build(),
                    Permission.builder().permissionName("ROLE:MANAGE").resource("ROLE").action("MANAGE").description("Quản lý roles & permissions").build(),
                    Permission.builder().permissionName("ADMIN:MANAGE").resource("ADMIN").action("MANAGE").description("Toàn quyền hệ thống (admin)").build(),
                    Permission.builder().permissionName("ADMIN:ANALYTICS").resource("ADMIN").action("READ").description("Xem phân tích hệ thống").build()
            ));
            log.info("Permissions seeded successfully.");
        }
        Permission doctorCreatePermission = ensurePermission(
                "DOCTOR:CREATE", "DOCTOR", "CREATE", "Tạo tài khoản bác sĩ");
        permissions = permissionRepository.findAll();

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

        Role adminRole = roles.stream()
                .filter(role -> role.getRoleName() == UserRole.ADMIN)
                .findFirst()
                .orElseThrow();
        ensureRolePermission(adminRole, doctorCreatePermission);
    }

    @Transactional
    public void seedServiceOfferings() {
        upsertServiceOffering(
                "SINGLE_PRENATAL_RELAX_MASSAGE",
                ServiceOfferingType.SINGLE,
                "Chăm sóc mẹ",
                "Massage thư giãn mẹ bầu (60 phút)",
                null,
                null,
                60,
                null,
                450000L,
                67500L,
                382500L,
                10);
        upsertServiceOffering(
                "SINGLE_LACTATION_CARE",
                ServiceOfferingType.SINGLE,
                "Chăm sóc mẹ",
                "Hỗ trợ kích sữa & chăm sóc tuyến vú",
                null,
                null,
                60,
                null,
                390000L,
                58500L,
                331500L,
                20);
        upsertServiceOffering(
                "SINGLE_BLOCKED_MILK_DUCT",
                ServiceOfferingType.SINGLE,
                "Chăm sóc mẹ",
                "Hỗ trợ thông tắc tia sữa (1 bên)",
                null,
                null,
                60,
                null,
                400000L,
                60000L,
                340000L,
                30);
        upsertServiceOffering(
                "SINGLE_BABY_LATCH_GUIDE",
                ServiceOfferingType.SINGLE,
                "Chăm sóc mẹ",
                "Hướng dẫn chỉnh tư thế cho bé bú & phục hồi cơ khớp",
                null,
                null,
                60,
                null,
                280000L,
                42000L,
                238000L,
                40);
        upsertServiceOffering(
                "SINGLE_NEWBORN_CARE_1H",
                ServiceOfferingType.SINGLE,
                "Chăm sóc mẹ",
                "Chăm sóc bé sơ sinh (1 giờ)",
                null,
                null,
                60,
                null,
                340000L,
                51000L,
                289000L,
                50);
        upsertServiceOffering(
                "SINGLE_NEWBORN_BATH",
                ServiceOfferingType.SINGLE,
                "Chăm sóc mẹ",
                "Tắm bé sơ sinh",
                null,
                null,
                45,
                null,
                180000L,
                27000L,
                153000L,
                60);
        upsertServiceOffering(
                "SINGLE_BABY_HEALTH_FOLLOWUP",
                ServiceOfferingType.SINGLE,
                "Chăm sóc mẹ",
                "Theo dõi sức khỏe bé & tư vấn chăm sóc",
                null,
                null,
                60,
                null,
                220000L,
                33000L,
                187000L,
                70);
        upsertServiceOffering(
                "COMBO_MASSAGE_LACTATION",
                ServiceOfferingType.SINGLE,
                "Combo Upsell",
                "Massage + kích sữa",
                null,
                null,
                120,
                null,
                720000L,
                108000L,
                612000L,
                80);
        upsertServiceOffering(
                "COMBO_BABY_CARE_BATH",
                ServiceOfferingType.SINGLE,
                "Combo Upsell",
                "Chăm sóc bé + tắm bé",
                null,
                null,
                90,
                null,
                480000L,
                72000L,
                408000L,
                90);
        upsertServiceOffering(
                "PACKAGE_SILVER",
                ServiceOfferingType.PACKAGE,
                "Gói dịch vụ",
                "Silver",
                "Phục hồi cơ bản sau sinh",
                "Massage mẹ 5 buổi, kích sữa 3 buổi, tắm bé 10 buổi, chăm sóc bé 10 buổi",
                null,
                10,
                6200000L,
                930000L,
                5270000L,
                110);
        upsertServiceOffering(
                "PACKAGE_GOLD",
                ServiceOfferingType.PACKAGE,
                "Gói dịch vụ",
                "Gold",
                "Phục hồi chuyên sâu",
                "Massage mẹ 11-12 buổi, kích sữa 5 buổi, tắm bé 20 buổi, chăm sóc bé 20 buổi",
                null,
                20,
                12000000L,
                1800000L,
                10200000L,
                120);
        upsertServiceOffering(
                "PACKAGE_DIAMOND",
                ServiceOfferingType.PACKAGE,
                "Gói dịch vụ",
                "Diamond",
                "Chăm sóc toàn diện tháng đầu",
                "Massage mẹ 17-18 buổi, kích sữa 7 buổi, tắm bé 30 buổi, chăm sóc bé 30 buổi",
                null,
                30,
                16800000L,
                2520000L,
                14280000L,
                130);

        log.info("Service offerings seed completed.");
    }

    private void upsertServiceOffering(String code,
                                       ServiceOfferingType type,
                                       String groupName,
                                       String serviceName,
                                       String fitDescription,
                                       String packageContent,
                                       Integer durationMinutes,
                                       Integer durationDays,
                                       Long grossAmount,
                                       Long platformFeeAmount,
                                       Long nurseEarningAmount,
                                       Integer sortOrder) {
        ServiceOffering serviceOffering = serviceOfferingRepository.findByServiceCode(code)
                .orElseGet(() -> ServiceOffering.builder()
                        .serviceCode(code)
                        .build());

        serviceOffering.setServiceType(type);
        serviceOffering.setGroupName(groupName);
        serviceOffering.setServiceName(serviceName);
        serviceOffering.setFitDescription(fitDescription);
        serviceOffering.setPackageContent(packageContent);
        serviceOffering.setDurationMinutes(durationMinutes);
        serviceOffering.setDurationDays(durationDays);
        serviceOffering.setGrossAmount(grossAmount);
        serviceOffering.setPlatformFeeAmount(platformFeeAmount);
        serviceOffering.setNurseEarningAmount(nurseEarningAmount);
        serviceOffering.setCommissionRate(BigDecimal.valueOf(15));
        serviceOffering.setIsActive(true);
        serviceOffering.setSortOrder(sortOrder);

        serviceOfferingRepository.save(serviceOffering);
    }

    private Permission ensurePermission(String permissionName, String resource, String action, String description) {
        return permissionRepository.findByPermissionName(permissionName)
                .orElseGet(() -> {
                    Permission permission = permissionRepository.save(Permission.builder()
                            .permissionName(permissionName)
                            .resource(resource)
                            .action(action)
                            .description(description)
                            .build());
                    log.info("Seeded missing permission: {}", permissionName);
                    return permission;
                });
    }

    private void ensureRolePermission(Role role, Permission permission) {
        if (rolePermissionRepository.existsByRoleAndPermission(role, permission)) {
            return;
        }

        rolePermissionRepository.save(RolePermission.builder()
                .role(role)
                .permission(permission)
                .build());
        log.info("Assigned permission {} to role {}", permission.getPermissionName(), role.getRoleName());
    }

    @Transactional
    public void seedAdminAccount() {
        if (!adminSeedEnabled) {
            log.info("Admin account seed is disabled.");
            return;
        }

        String phone = normalize(adminPhone);
        String password = normalize(adminPassword);
        if (phone == null || password == null) {
            log.warn("Skipping admin account seed because app.seed.admin.phone or app.seed.admin.password is missing.");
            return;
        }

        Role adminRole = roleRepository.findByRoleName(UserRole.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role must be seeded before admin account"));

        String username = phone;
        String cognitoSub = ensureAdminCognitoUser(username, password, phone);
        User admin = userRepository.findByPhoneWithRolesAndProviders(phone)
                .or(() -> Optional.ofNullable(cognitoSub).flatMap(userRepository::findByCognitoSubWithRolesAndProviders))
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName(adminFullName)
                        .cognitoUsername(username)
                        .cognitoSub(cognitoSub)
                        .phone(phone)
                        .phoneVerified(true)
                        .email(normalize(adminEmail))
                        .emailVerified(normalize(adminEmail) != null)
                        .isActive(true)
                        .build()));

        boolean changed = syncAdminUser(admin, adminRole, username, cognitoSub, phone);
        if (changed) {
            userRepository.save(admin);
        }

        ensureAdminRoleAssignment(admin, adminRole);
        ensureLocalIdentityProvider(admin, username);
        ensureAdminCognitoGroup(username);
        log.info("Admin account seed completed for username={}", username);
    }

    private String ensureAdminCognitoUser(String username, String password, String phone) {
        try {
            String existingSub = cognitoService.adminGetUserSub(username);
            log.info("Admin Cognito user already exists: username={}", username);
            return existingSub;
        } catch (UserNotFoundException e) {
            String createdSub = cognitoService.adminCreateUser(
                    username, password, adminFullName, normalize(adminEmail), phone);
            log.info("Admin Cognito user created: username={}", username);
            return createdSub;
        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to initialize admin Cognito user: username={} message={}",
                    username, e.awsErrorDetails().errorMessage(), e);
            throw e;
        }
    }

    private boolean syncAdminUser(User admin, Role adminRole, String username, String cognitoSub, String phone) {
        boolean changed = false;

        if (admin.getCognitoUsername() == null || admin.getCognitoUsername().isBlank()) {
            admin.setCognitoUsername(username);
            changed = true;
        }
        if (cognitoSub != null && !cognitoSub.isBlank() && !cognitoSub.equals(admin.getCognitoSub())) {
            admin.setCognitoSub(cognitoSub);
            changed = true;
        }
        if (admin.getPhone() == null || admin.getPhone().isBlank()) {
            admin.setPhone(phone);
            changed = true;
        }
        if (!Boolean.TRUE.equals(admin.getPhoneVerified())) {
            admin.setPhoneVerified(true);
            changed = true;
        }
        String email = normalize(adminEmail);
        if (email != null && (admin.getEmail() == null || admin.getEmail().isBlank())) {
            admin.setEmail(email);
            admin.setEmailVerified(true);
            changed = true;
        }
        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            admin.setIsActive(true);
            changed = true;
        }
        if (!admin.hasRole(adminRole.getRoleName())) {
            changed = true;
        }

        return changed;
    }

    private void ensureAdminRoleAssignment(User admin, Role adminRole) {
        if (userRoleAssignmentRepository.existsByUserAndRole(admin, adminRole)) {
            return;
        }

        userRoleAssignmentRepository.save(UserRoleAssignment.builder()
                .user(admin)
                .role(adminRole)
                .build());
        log.info("Assigned ADMIN role to seeded admin account: userId={}", admin.getId());
    }

    private void ensureLocalIdentityProvider(User admin, String username) {
        Optional<UserIdentityProvider> existing =
                userIdentityProviderRepository.findByUserAndProvider(admin, AuthProvider.LOCAL);
        if (existing.isPresent()) {
            UserIdentityProvider provider = existing.get();
            if (!username.equals(provider.getProviderUid())) {
                provider.setProviderUid(username);
                userIdentityProviderRepository.save(provider);
            }
            return;
        }

        userIdentityProviderRepository.save(UserIdentityProvider.builder()
                .user(admin)
                .provider(AuthProvider.LOCAL)
                .providerUid(username)
                .build());
    }

    private void ensureAdminCognitoGroup(String username) {
        try {
            cognitoService.adminAddUserToGroup(username, UserRole.ADMIN.name());
        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to assign ADMIN Cognito group: username={} message={}",
                    username, e.awsErrorDetails().errorMessage(), e);
            throw e;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
