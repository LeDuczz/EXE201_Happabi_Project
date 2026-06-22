package com.minduc.happabi.seed;

import com.minduc.happabi.entity.Permission;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.RolePermission;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.ServiceOfferingRequiredSkill;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserIdentityProvider;
import com.minduc.happabi.entity.UserRoleAssignment;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.NurseSkill;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.PermissionRepository;
import com.minduc.happabi.repository.RolePermissionRepository;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.ServiceOfferingRequiredSkillRepository;
import com.minduc.happabi.repository.ServiceOfferingRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.repository.UserRoleAssignmentRepository;
import com.minduc.happabi.integration.cognito.CognitoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ServiceOfferingRequiredSkillRepository serviceOfferingRequiredSkillRepository;
    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserIdentityProviderRepository userIdentityProviderRepository;
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
    public void seedAdminAccount() {
        if (!adminSeedEnabled) {
            return;
        }

        String phone = normalize(adminPhone);
        String password = normalize(adminPassword);
        if (phone == null || password == null) {
            log.info("[Seed] Admin bootstrap skipped because credentials are not configured.");
            return;
        }

        Role adminRole = roleRepository.findByRoleName(UserRole.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role must be seeded before admin bootstrap."));
        String cognitoSub = ensureAdminCognitoUser(phone, password, phone);
        User admin = userRepository.findByPhoneWithRolesAndProviders(phone)
                .or(() -> Optional.ofNullable(cognitoSub).flatMap(userRepository::findByCognitoSubWithRolesAndProviders))
                .orElseGet(() -> User.builder()
                        .fullName(adminFullName)
                        .cognitoUsername(phone)
                        .cognitoSub(cognitoSub)
                        .phone(phone)
                        .phoneVerified(true)
                        .email(normalize(adminEmail))
                        .emailVerified(normalize(adminEmail) != null)
                        .isActive(true)
                        .build());

        admin.setFullName(adminFullName);
        admin.setCognitoUsername(phone);
        admin.setCognitoSub(cognitoSub);
        admin.setPhone(phone);
        admin.setPhoneVerified(true);
        admin.setIsActive(true);
        if (normalize(adminEmail) != null) {
            admin.setEmail(normalize(adminEmail));
            admin.setEmailVerified(true);
        }
        admin = userRepository.save(admin);
        ensureAdminRoleAssignment(admin, adminRole);
        ensureLocalIdentityProvider(admin, phone);
        cognitoService.adminAddUserToGroup(phone, UserRole.ADMIN.name());
        log.info("[Seed] Admin bootstrap completed for username={}", phone);
    }

    private String ensureAdminCognitoUser(String username, String password, String phone) {
        try {
            return cognitoService.adminGetUserSub(username);
        } catch (UserNotFoundException ignored) {
            return cognitoService.adminCreateUser(username, password, adminFullName, normalize(adminEmail), phone);
        } catch (CognitoIdentityProviderException exception) {
            throw exception;
        }
    }

    private void ensureAdminRoleAssignment(User admin, Role adminRole) {
        if (!userRoleAssignmentRepository.existsByUserAndRole(admin, adminRole)) {
            userRoleAssignmentRepository.save(UserRoleAssignment.builder()
                    .user(admin)
                    .role(adminRole)
                    .build());
        }
    }

    private void ensureLocalIdentityProvider(User admin, String username) {
        UserIdentityProvider provider = userIdentityProviderRepository
                .findByUserAndProvider(admin, AuthProvider.LOCAL)
                .orElseGet(() -> UserIdentityProvider.builder()
                        .user(admin)
                        .provider(AuthProvider.LOCAL)
                        .build());
        provider.setProviderUid(username);
        userIdentityProviderRepository.save(provider);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

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
                120,
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
                120,
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
                60,
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
                120,
                null,
                480000L,
                72000L,
                408000L,
                90);
        seedRequiredSkills();

        log.info("Service offerings seed completed.");
    }

    private void seedRequiredSkills() {
        upsertRequiredSkills("SINGLE_PRENATAL_RELAX_MASSAGE",
                NurseSkill.PRENATAL_RELAXATION_MASSAGE,
                NurseSkill.PARENT_COMMUNICATION,
                NurseSkill.CUSTOMER_CARE);
        upsertRequiredSkills("SINGLE_LACTATION_CARE",
                NurseSkill.LACTATION_STIMULATION,
                NurseSkill.BREAST_CARE,
                NurseSkill.MOTHER_BABY_CONSULTING);
        upsertRequiredSkills("SINGLE_BLOCKED_MILK_DUCT",
                NurseSkill.BLOCKED_MILK_DUCT_SUPPORT,
                NurseSkill.BREAST_CARE,
                NurseSkill.SITUATION_HANDLING);
        upsertRequiredSkills("SINGLE_BABY_LATCH_GUIDE",
                NurseSkill.BREASTFEEDING_POSITION_GUIDANCE,
                NurseSkill.MOTHER_BABY_CONSULTING,
                NurseSkill.PARENT_COMMUNICATION);
        upsertRequiredSkills("SINGLE_NEWBORN_CARE_1H",
                NurseSkill.NEWBORN_BASIC_CARE,
                NurseSkill.NEWBORN_HEALTH_MONITORING,
                NurseSkill.NEWBORN_WARNING_SIGN_RECOGNITION);
        upsertRequiredSkills("SINGLE_NEWBORN_BATH",
                NurseSkill.NEWBORN_BATHING,
                NurseSkill.NEWBORN_BASIC_CARE,
                NurseSkill.NEWBORN_SKIN_CARE);
        upsertRequiredSkills("SINGLE_BABY_HEALTH_FOLLOWUP",
                NurseSkill.NEWBORN_HEALTH_MONITORING,
                NurseSkill.HOME_NEWBORN_CARE_GUIDANCE,
                NurseSkill.MOTHER_BABY_CONSULTING);
        upsertRequiredSkills("COMBO_MASSAGE_LACTATION",
                NurseSkill.POSTPARTUM_RECOVERY_MASSAGE,
                NurseSkill.LACTATION_STIMULATION,
                NurseSkill.BREAST_CARE);
        upsertRequiredSkills("COMBO_BABY_CARE_BATH",
                NurseSkill.NEWBORN_BASIC_CARE,
                NurseSkill.NEWBORN_BATHING,
                NurseSkill.NEWBORN_SKIN_CARE);
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

    private void upsertRequiredSkills(String serviceCode, NurseSkill... skills) {
        ServiceOffering serviceOffering = serviceOfferingRepository.findByServiceCode(serviceCode)
                .orElseThrow(() -> new IllegalStateException("Service offering must be seeded before required skills: " + serviceCode));
        Set<NurseSkill> nextSkills = Arrays.stream(skills).collect(Collectors.toSet());
        serviceOfferingRequiredSkillRepository.deleteByServiceOfferingAndSkillNotIn(serviceOffering, nextSkills);
        nextSkills.forEach(skill -> serviceOfferingRequiredSkillRepository.findByServiceOfferingAndSkill(serviceOffering, skill)
                .orElseGet(() -> serviceOfferingRequiredSkillRepository.save(ServiceOfferingRequiredSkill.builder()
                        .serviceOffering(serviceOffering)
                        .skill(skill)
                        .build())));
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


}
