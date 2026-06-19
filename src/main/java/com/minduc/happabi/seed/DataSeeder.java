package com.minduc.happabi.seed;

import com.minduc.happabi.entity.Permission;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseSkillEntity;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.RolePermission;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.ServiceOfferingRequiredSkill;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserIdentityProvider;
import com.minduc.happabi.entity.UserRoleAssignment;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.NurseSkill;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.NurseCertificationRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseSkillRepository;
import com.minduc.happabi.repository.PermissionRepository;
import com.minduc.happabi.repository.RolePermissionRepository;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.ServiceOfferingRequiredSkillRepository;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ServiceOfferingRequiredSkillRepository serviceOfferingRequiredSkillRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final NurseCertificationRepository nurseCertificationRepository;
    private final NurseSkillRepository nurseSkillRepository;
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

    @Value("${app.seed.demo-nurses.enabled:true}")
    private boolean demoNurseSeedEnabled;

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

    @Transactional
    public void seedDemoNurses() {
        if (!demoNurseSeedEnabled) {
            log.info("Demo nurse seed is disabled.");
            return;
        }

        Role nurseRole = roleRepository.findByRoleName(UserRole.NURSE)
                .orElseThrow(() -> new IllegalStateException("NURSE role must be seeded before demo nurses"));

        upsertDemoNurse(nurseRole, new DemoNurseSeed(
                "demo-nurse-lan",
                "+84901000001",
                "lan.demo@happabi.local",
                "Nguyễn Thị Lan",
                NurseSpecialty.MIDWIFE,
                7,
                AvailabilityStatus.AVAILABLE,
                BigDecimal.valueOf(4.9),
                48,
                132,
                BigDecimal.valueOf(96),
                "Hỗ trợ mẹ sau sinh và chăm bé sơ sinh, đặc biệt các ca bé khó bú, chăm rốn và theo dõi dấu hiệu bất thường.",
                "Quận 1, Quận 3, Bình Thạnh",
                "Hồ Chí Minh",
                true,
                true,
                List.of(
                        NurseSkill.POSTPARTUM_RECOVERY_MASSAGE,
                        NurseSkill.PRENATAL_RELAXATION_MASSAGE,
                        NurseSkill.LACTATION_STIMULATION,
                        NurseSkill.BLOCKED_MILK_DUCT_SUPPORT,
                        NurseSkill.BREAST_CARE,
                        NurseSkill.BREASTFEEDING_POSITION_GUIDANCE,
                        NurseSkill.POSTPARTUM_HEALTH_MONITORING,
                        NurseSkill.NEWBORN_BATHING,
                        NurseSkill.NEWBORN_BASIC_CARE,
                        NurseSkill.NEWBORN_HEALTH_MONITORING,
                        NurseSkill.NEWBORN_SKIN_CARE,
                        NurseSkill.HOME_NEWBORN_CARE_GUIDANCE,
                        NurseSkill.NEWBORN_WARNING_SIGN_RECOGNITION,
                        NurseSkill.PARENT_COMMUNICATION,
                        NurseSkill.MOTHER_BABY_CONSULTING,
                        NurseSkill.SITUATION_HANDLING,
                        NurseSkill.CUSTOMER_CARE,
                        NurseSkill.SCHEDULE_MANAGEMENT
                ),
                List.of(
                        new DemoCertificationSeed("Chứng chỉ hộ sinh", "Đại học Y Dược TP.HCM", 2018),
                        new DemoCertificationSeed("Chăm sóc trẻ sơ sinh nâng cao", "Bệnh viện Từ Dũ", 2021)
                )
        ));

        upsertDemoNurse(nurseRole, new DemoNurseSeed(
                "demo-nurse-huong",
                "+84901000002",
                "huong.demo@happabi.local",
                "Trần Thu Hương",
                NurseSpecialty.NURSE,
                4,
                AvailabilityStatus.AVAILABLE,
                BigDecimal.valueOf(4.7),
                26,
                74,
                BigDecimal.valueOf(91),
                "Điều dưỡng có kinh nghiệm chăm sóc sau sinh tại nhà, hướng dẫn tắm bé và theo dõi sức khỏe hằng ngày.",
                "Quận 7, Nhà Bè, Bình Chánh",
                "Hồ Chí Minh",
                true,
                false,
                List.of(
                        NurseSkill.NEWBORN_BATHING,
                        NurseSkill.NEWBORN_BASIC_CARE,
                        NurseSkill.NEWBORN_HEALTH_MONITORING,
                        NurseSkill.NEWBORN_SKIN_CARE,
                        NurseSkill.HOME_NEWBORN_CARE_GUIDANCE,
                        NurseSkill.NEWBORN_WARNING_SIGN_RECOGNITION,
                        NurseSkill.PARENT_COMMUNICATION,
                        NurseSkill.MOTHER_BABY_CONSULTING,
                        NurseSkill.CUSTOMER_CARE,
                        NurseSkill.SCHEDULE_MANAGEMENT
                ),
                List.of(
                        new DemoCertificationSeed("Điều dưỡng đa khoa", "Cao đẳng Y tế TP.HCM", 2019)
                )
        ));

        upsertDemoNurse(nurseRole, new DemoNurseSeed(
                "demo-nurse-mai",
                "+84901000003",
                "mai.demo@happabi.local",
                "Lê Thanh Mai",
                NurseSpecialty.CAREGIVER,
                2,
                AvailabilityStatus.BUSY,
                BigDecimal.valueOf(4.4),
                9,
                31,
                BigDecimal.valueOf(84),
                "Tập trung hỗ trợ sinh hoạt cho mẹ sau sinh, massage thư giãn và đồng hành chăm bé theo lịch gia đình.",
                "Thủ Đức, Dĩ An",
                "Hồ Chí Minh",
                false,
                false,
                List.of(
                        NurseSkill.POSTPARTUM_RECOVERY_MASSAGE,
                        NurseSkill.PRENATAL_RELAXATION_MASSAGE,
                        NurseSkill.FOOT_PAIN_RELIEF_MASSAGE,
                        NurseSkill.PARENT_COMMUNICATION,
                        NurseSkill.CUSTOMER_CARE
                ),
                List.of()
        ));

        upsertDemoNurse(nurseRole, new DemoNurseSeed(
                "demo-nurse-ngoc",
                "+84901000004",
                "ngoc.demo@happabi.local",
                "Phạm Bảo Ngọc",
                NurseSpecialty.NURSE,
                9,
                AvailabilityStatus.AVAILABLE,
                BigDecimal.valueOf(4.8),
                61,
                180,
                BigDecimal.valueOf(98),
                "Mạnh về theo dõi sức khỏe bé, chăm sóc vết mổ sau sinh và hỗ trợ mẹ có dấu hiệu tắc tia sữa.",
                "Cầu Giấy, Tây Hồ, Ba Đình",
                "Hà Nội",
                true,
                true,
                List.of(
                        NurseSkill.POSTPARTUM_RECOVERY_MASSAGE,
                        NurseSkill.BLOCKED_MILK_DUCT_SUPPORT,
                        NurseSkill.BREAST_CARE,
                        NurseSkill.BREASTFEEDING_POSITION_GUIDANCE,
                        NurseSkill.POSTPARTUM_HEALTH_MONITORING,
                        NurseSkill.NEWBORN_BASIC_CARE,
                        NurseSkill.NEWBORN_HEALTH_MONITORING,
                        NurseSkill.NEWBORN_WARNING_SIGN_RECOGNITION,
                        NurseSkill.HOME_NEWBORN_CARE_GUIDANCE,
                        NurseSkill.MOTHER_BABY_CONSULTING,
                        NurseSkill.SITUATION_HANDLING,
                        NurseSkill.SCHEDULE_MANAGEMENT
                ),
                List.of(
                        new DemoCertificationSeed("Điều dưỡng nhi khoa", "Bệnh viện Nhi Trung ương", 2017),
                        new DemoCertificationSeed("Tư vấn nuôi con bằng sữa mẹ", "Hiệp hội Sữa mẹ Việt Nam", 2020)
                )
        ));

        upsertDemoNurse(nurseRole, new DemoNurseSeed(
                "demo-nurse-thao",
                "+84901000005",
                "thao.demo@happabi.local",
                "Đỗ Minh Thảo",
                NurseSpecialty.MIDWIFE,
                5,
                AvailabilityStatus.OFFLINE,
                BigDecimal.valueOf(4.6),
                18,
                52,
                BigDecimal.valueOf(88),
                "Hỗ trợ mẹ mới sinh về phục hồi cơ bản, tắm bé, chăm rốn và nhắc lịch theo dõi sau sinh.",
                "Hải Châu, Thanh Khê",
                "Đà Nẵng",
                true,
                false,
                List.of(
                        NurseSkill.POSTPARTUM_RECOVERY_MASSAGE,
                        NurseSkill.NEWBORN_BATHING,
                        NurseSkill.NEWBORN_BASIC_CARE,
                        NurseSkill.NEWBORN_SKIN_CARE,
                        NurseSkill.PARENT_COMMUNICATION,
                        NurseSkill.CUSTOMER_CARE
                ),
                List.of(
                        new DemoCertificationSeed("Hộ sinh cơ bản", "Cao đẳng Y tế Đà Nẵng", 2018)
                )
        ));

        log.info("Demo nurse seed completed.");
    }

    private void upsertDemoNurse(Role nurseRole, DemoNurseSeed seed) {
        User user = userRepository.findByPhone(seed.phone())
                .or(() -> userRepository.findByEmail(seed.email()))
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName(seed.fullName())
                        .cognitoUsername(seed.username())
                        .cognitoSub(seed.username())
                        .phone(seed.phone())
                        .phoneVerified(true)
                        .email(seed.email())
                        .emailVerified(true)
                        .isActive(true)
                        .build()));

        boolean userChanged = false;
        if (!seed.fullName().equals(user.getFullName())) {
            user.setFullName(seed.fullName());
            userChanged = true;
        }
        if (user.getCognitoUsername() == null || user.getCognitoUsername().isBlank()) {
            user.setCognitoUsername(seed.username());
            userChanged = true;
        }
        if (user.getCognitoSub() == null || user.getCognitoSub().isBlank()) {
            user.setCognitoSub(seed.username());
            userChanged = true;
        }
        if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
            user.setPhoneVerified(true);
            userChanged = true;
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
            userChanged = true;
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            user.setIsActive(true);
            userChanged = true;
        }
        if (userChanged) {
            userRepository.save(user);
        }

        ensureUserRoleAssignment(user, nurseRole);
        ensureLocalIdentityProvider(user, seed.username());

        NurseProfile profile = nurseProfileRepository.findByUser(user)
                .orElseGet(() -> NurseProfile.builder()
                        .user(user)
                        .licenseNumber("DEMO-" + seed.username().toUpperCase())
                        .build());

        profile.setSpecialty(seed.specialty());
        profile.setExperienceYears(seed.experienceYears());
        profile.setNurseStatus(NurseStatus.ACTIVE);
        profile.setAvailabilityStatus(seed.availabilityStatus());
        profile.setRatingAvg(seed.ratingAvg());
        profile.setTotalReviews(seed.totalReviews());
        profile.setTotalCompletedJobs(seed.totalCompletedJobs());
        profile.setResponseRate(seed.responseRate());
        profile.setBio(seed.bio());
        profile.setServiceArea(seed.serviceArea());
        profile.setCity(seed.city());
        profile.setBackgroundChecked(seed.backgroundChecked());
        profile.setIsFeatured(seed.featured());
        profile.setLastStatusChangedAt(OffsetDateTime.now());

        NurseProfile savedProfile = nurseProfileRepository.save(profile);
        upsertDemoSkills(savedProfile, seed.skills());
        upsertDemoCertifications(savedProfile, seed.certifications());
    }

    private void ensureUserRoleAssignment(User user, Role role) {
        if (userRoleAssignmentRepository.existsByUserAndRole(user, role)) {
            return;
        }
        userRoleAssignmentRepository.save(UserRoleAssignment.builder()
                .user(user)
                .role(role)
                .build());
    }

    private void upsertDemoCertifications(NurseProfile profile, List<DemoCertificationSeed> certifications) {
        List<NurseCertification> existing = nurseCertificationRepository.findByNurseOrderByIdDesc(profile);
        for (DemoCertificationSeed seed : certifications) {
            NurseCertification certification = existing.stream()
                    .filter(item -> seed.certName().equalsIgnoreCase(item.getCertName()))
                    .findFirst()
                    .orElseGet(() -> NurseCertification.builder()
                            .nurse(profile)
                            .certName(seed.certName())
                            .build());

            certification.setIssuedBy(seed.issuedBy());
            certification.setIssuedDate(LocalDate.of(seed.issuedYear(), 1, 1));
            certification.setExpiryDate(LocalDate.of(seed.issuedYear() + 10, 12, 31));
            certification.setIsVerified(true);
            certification.setVerifiedAt(OffsetDateTime.now());
            nurseCertificationRepository.save(certification);
        }
    }

    private void upsertDemoSkills(NurseProfile profile, List<NurseSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        skills.forEach(skill -> {
            NurseSkillEntity entity = nurseSkillRepository.findByNurseAndSkill(profile, skill)
                    .orElseGet(() -> NurseSkillEntity.builder()
                            .nurse(profile)
                            .skill(skill)
                            .build());
            if (entity.getVerifiedAt() == null) {
                entity.setVerifiedAt(now);
            }
            nurseSkillRepository.save(entity);
        });
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
                        .cognitoUsername(cognitoSub)
                        .cognitoSub(cognitoSub)
                        .phone(phone)
                        .phoneVerified(true)
                        .email(normalize(adminEmail))
                        .emailVerified(normalize(adminEmail) != null)
                        .isActive(true)
                        .build()));

        boolean changed = syncAdminUser(admin, adminRole, cognitoSub, cognitoSub, phone);
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

    private boolean syncAdminUser(User admin, Role adminRole, String canonicalUsername, String cognitoSub, String phone) {
        boolean changed = false;

        if (canonicalUsername != null
                && !canonicalUsername.isBlank()
                && !canonicalUsername.equals(admin.getCognitoUsername())) {
            admin.setCognitoUsername(canonicalUsername);
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

    private record DemoNurseSeed(String username,
                                 String phone,
                                 String email,
                                 String fullName,
                                 NurseSpecialty specialty,
                                 Integer experienceYears,
                                 AvailabilityStatus availabilityStatus,
                                 BigDecimal ratingAvg,
                                 Integer totalReviews,
                                 Integer totalCompletedJobs,
                                 BigDecimal responseRate,
                                 String bio,
                                 String serviceArea,
                                 String city,
                                 Boolean backgroundChecked,
                                 Boolean featured,
                                 List<NurseSkill> skills,
                                 List<DemoCertificationSeed> certifications) {
    }

    private record DemoCertificationSeed(String certName,
                                         String issuedBy,
                                         Integer issuedYear) {
    }
}
