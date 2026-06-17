package com.minduc.happabi.seed;

import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingSlot;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseReview;
import com.minduc.happabi.entity.NurseSkillEntity;
import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserIdentityProvider;
import com.minduc.happabi.entity.UserRoleAssignment;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.entity.WorkSessionChecklistItem;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingSlotStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.EkycStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.NurseContractStatus;
import com.minduc.happabi.enums.NurseReviewTag;
import com.minduc.happabi.enums.NurseSkill;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.enums.TransactionType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.enums.WorkSessionChecklistStatus;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.integration.cognito.CognitoService;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.NotificationRepository;
import com.minduc.happabi.repository.NurseContractRepository;
import com.minduc.happabi.repository.NurseKycRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseReviewRepository;
import com.minduc.happabi.repository.NurseSkillRepository;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.ServiceOfferingRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.repository.UserRoleAssignmentRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.repository.WorkSessionChecklistItemRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataSeeder {

    private final CognitoService cognitoService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserIdentityProviderRepository userIdentityProviderRepository;
    private final MotherProfileRepository motherProfileRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final NurseSkillRepository nurseSkillRepository;
    private final NurseKycRepository nurseKycRepository;
    private final NurseContractRepository nurseContractRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final WorkSessionRepository workSessionRepository;
    private final WorkSessionChecklistItemRepository checklistItemRepository;
    private final NurseReviewRepository nurseReviewRepository;
    private final NurseWalletRepository nurseWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final NotificationRepository notificationRepository;

    @Value("${app.seed.test-data.enabled:true}")
    private boolean enabled;

    @Value("${app.seed.test-data.password:Happabi@12345}")
    private String password;

    @Transactional
    public void seedAll() {
        if (!enabled) {
            log.info("Test data seed is disabled.");
            return;
        }

        log.info("Seeding Happabi test data...");

        User mother = ensureLoginUser(new TestAccount(
                "+84902000001", "mother.test@happabi.local", "Mother Test", UserRole.MOTHER));
        User motherPremium = ensureLoginUser(new TestAccount(
                "+84902000002", "mother.premium@happabi.local", "Mother Premium", UserRole.MOTHER));
        User nurseActiveUser = ensureLoginUser(new TestAccount(
                "+84902000011", "nurse.active@happabi.local", "Nurse Active", UserRole.NURSE));
        User nursePendingUser = ensureLoginUser(new TestAccount(
                "+84902000012", "nurse.pending@happabi.local", "Nurse Pending", UserRole.NURSE));
        User nurseSuspendedUser = ensureLoginUser(new TestAccount(
                "+84902000013", "nurse.suspended@happabi.local", "Nurse Suspended", UserRole.NURSE));
        User doctor = ensureLoginUser(new TestAccount(
                "+84902000021", "doctor.test@happabi.local", "Doctor Reviewer", UserRole.DOCTOR));
        User admin = ensureLoginUser(new TestAccount(
                "+84902000031", "admin.test@happabi.local", "Admin Test", UserRole.ADMIN));

        ensureMotherProfile(mother, "12 Nguyen Hue, District 1", "Ho Chi Minh", 10);
        ensureMotherProfile(motherPremium, "88 Tran Duy Hung, Cau Giay", "Ha Noi", 45);

        NurseProfile nurseActive = ensureNurseProfile(nurseActiveUser, "TEST-NURSE-ACTIVE",
                NurseStatus.ACTIVE, AvailabilityStatus.AVAILABLE, true, doctor, admin);
        NurseProfile nursePending = ensureNurseProfile(nursePendingUser, "TEST-NURSE-PENDING",
                NurseStatus.PENDING_REVIEW, AvailabilityStatus.OFFLINE, false, doctor, null);
        NurseProfile nurseSuspended = ensureNurseProfile(nurseSuspendedUser, "TEST-NURSE-SUSPENDED",
                NurseStatus.SUSPENDED, AvailabilityStatus.OFFLINE, true, doctor, admin);

        ensureWallet(nurseActive, 2500000L, 700000L);
        ensureWallet(nursePending, 150000L, 0L);
        ensureWallet(nurseSuspended, 950000L, 300000L);

        ServiceOffering singleCare = requireService("SINGLE_NEWBORN_CARE_1H");
        ServiceOffering bath = requireService("SINGLE_NEWBORN_BATH");

        WorkSession scheduled = ensureBookingFlow("TEST-SCHEDULED-001", mother, nurseActive, singleCare,
                BookingStatus.ACCEPTED, WorkSessionStatus.SCHEDULED, OffsetDateTime.now().plusDays(1).withHour(9).withMinute(0),
                "Can chuan bi khan tam va dau massage cho be.", false);
        WorkSession inProgress = ensureBookingFlow("TEST-IN-PROGRESS-001", mother, nurseActive, bath,
                BookingStatus.ACCEPTED, WorkSessionStatus.IN_PROGRESS, OffsetDateTime.now().minusMinutes(30),
                "Nurse dang thuc hien dich vu tam be.", false);
        WorkSession pendingConfirm = ensureBookingFlow("TEST-PENDING-CONFIRM-001", motherPremium, nurseActive, singleCare,
                BookingStatus.ACCEPTED, WorkSessionStatus.PENDING_MOTHER_CONFIRMATION, OffsetDateTime.now().minusDays(1),
                "Cho me xac nhan hoan thanh buoi cham soc.", false);
        WorkSession completed = ensureBookingFlow("TEST-COMPLETED-001", mother, nurseActive, singleCare,
                BookingStatus.COMPLETED, WorkSessionStatus.COMPLETED, OffsetDateTime.now().minusDays(5),
                "Buoi cham soc da hoan thanh va co danh gia.", true);
        ensureBookingFlow("TEST-CANCELLED-001", motherPremium, nurseActive, bath,
                BookingStatus.CANCELLED, WorkSessionStatus.CANCELLED, OffsetDateTime.now().plusDays(3).withHour(14),
                "Booking huy de test trang thai cancel.", false);
        ensureReview(completed, 5, "Nurse den dung gio, giao tiep ro rang va cham be rat nhe nhang.");

        ensureNotification(mother, NotificationType.WORK_SESSION_UPDATED, "Lich cham soc sap toi",
                "Ban co mot lich cham soc vao ngay mai luc 09:00.", "WORK_SESSION", scheduled.getId().toString(), false);
        ensureNotification(nurseActiveUser, NotificationType.WORK_SESSION_UPDATED, "Dang co buoi cham soc",
                "Hay cap nhat checklist va check-out khi hoan thanh.", "WORK_SESSION", inProgress.getId().toString(), false);
        ensureNotification(nursePendingUser, NotificationType.NURSE_PROFILE_APPROVED_PENDING_CONTRACT,
                "Ho so dang cho duyet", "Ho so test dang o trang thai pending review.", "NURSE_PROFILE",
                nursePending.getId().toString(), true);
        ensureNotification(nurseSuspendedUser, NotificationType.NURSE_SUSPENDED,
                "Tai khoan dang tam dung", "Tai khoan nurse test dang o trang thai suspended.", "NURSE_PROFILE",
                nurseSuspended.getId().toString(), false);

        log.info("Test data seed completed. Password for all test accounts: {}", password);
    }

    private User ensureLoginUser(TestAccount account) {
        String cognitoSub = ensureCognitoAccount(account);
        Role role = roleRepository.findByRoleName(account.role())
                .orElseThrow(() -> new IllegalStateException("Missing role: " + account.role()));

        User user = userRepository.findByPhoneWithRolesAndProviders(account.phone())
                .or(() -> userRepository.findByEmailWithRolesAndProviders(account.email()))
                .or(() -> userRepository.findByCognitoSubWithRolesAndProviders(cognitoSub))
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName(account.fullName())
                        .phone(account.phone())
                        .email(account.email())
                        .isActive(true)
                        .build()));

        user.setFullName(account.fullName());
        user.setPhone(account.phone());
        user.setPhoneVerified(true);
        user.setEmail(account.email());
        user.setEmailVerified(true);
        user.setCognitoUsername(account.phone());
        user.setCognitoSub(cognitoSub);
        user.setIsActive(true);
        user = userRepository.save(user);

        ensureRole(user, role);
        ensureLocalProvider(user, account.phone());
        return user;
    }

    private String ensureCognitoAccount(TestAccount account) {
        try {
            String sub = cognitoService.adminGetUserSub(account.phone());
            cognitoService.adminSetPermanentPassword(account.phone(), password);
            cognitoService.adminUpdatePhoneNumber(account.phone(), account.phone(), true);
            cognitoService.adminAddUserToGroup(account.phone(), account.role().name());
            return sub;
        } catch (UserNotFoundException e) {
            String sub = cognitoService.adminCreateUser(
                    account.phone(), password, account.fullName(), account.email(), account.phone());
            cognitoService.adminAddUserToGroup(account.phone(), account.role().name());
            return sub;
        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to sync test Cognito account {}: {}", account.phone(),
                    e.awsErrorDetails().errorMessage(), e);
            throw e;
        }
    }

    private void ensureRole(User user, Role role) {
        if (!userRoleAssignmentRepository.existsByUserAndRole(user, role)) {
            userRoleAssignmentRepository.save(UserRoleAssignment.builder().user(user).role(role).build());
        }
    }

    private void ensureLocalProvider(User user, String username) {
        Optional<UserIdentityProvider> existing =
                userIdentityProviderRepository.findByUserAndProvider(user, AuthProvider.LOCAL);
        if (existing.isPresent()) {
            UserIdentityProvider provider = existing.get();
            provider.setProviderUid(username);
            userIdentityProviderRepository.save(provider);
            return;
        }
        userIdentityProviderRepository.save(UserIdentityProvider.builder()
                .user(user)
                .provider(AuthProvider.LOCAL)
                .providerUid(username)
                .build());
    }

    private void ensureMotherProfile(User user, String address, String city, int babyAgeDays) {
        MotherProfile profile = motherProfileRepository.findByUser(user)
                .orElseGet(() -> MotherProfile.builder().user(user).build());
        profile.setAddress(address);
        profile.setCity(city);
        profile.setDayOfBirth(LocalDate.now().minusYears(29));
        profile.setBabyBirthDate(LocalDate.now().minusDays(babyAgeDays));
        profile.setLat(BigDecimal.valueOf(10.7769));
        profile.setLng(BigDecimal.valueOf(106.7009));
        motherProfileRepository.save(profile);
    }

    private NurseProfile ensureNurseProfile(User user, String licenseNumber, NurseStatus status,
                                            AvailabilityStatus availability, boolean signedContract,
                                            User doctor, User admin) {
        NurseProfile profile = nurseProfileRepository.findByUser(user)
                .orElseGet(() -> NurseProfile.builder().user(user).licenseNumber(licenseNumber).build());
        profile.setLicenseNumber(licenseNumber);
        profile.setSpecialty(NurseSpecialty.MIDWIFE);
        profile.setExperienceYears(6);
        profile.setNurseStatus(status);
        profile.setAvailabilityStatus(availability);
        profile.setRatingAvg(BigDecimal.valueOf(status == NurseStatus.ACTIVE ? 4.8 : 0));
        profile.setTotalReviews(status == NurseStatus.ACTIVE ? 24 : 0);
        profile.setTotalCompletedJobs(status == NurseStatus.ACTIVE ? 78 : 0);
        profile.setResponseRate(BigDecimal.valueOf(status == NurseStatus.ACTIVE ? 97 : 80));
        profile.setBio("Test nurse account for Happabi flow QA.");
        profile.setServiceArea("District 1, District 3, Binh Thanh");
        profile.setAddress("20 Pasteur, District 1");
        profile.setCity("Ho Chi Minh");
        profile.setLat(BigDecimal.valueOf(10.7800));
        profile.setLng(BigDecimal.valueOf(106.6990));
        profile.setBackgroundChecked(status == NurseStatus.ACTIVE || status == NurseStatus.SUSPENDED);
        profile.setIsFeatured(status == NurseStatus.ACTIVE);
        profile.setLastStatusChangedAt(OffsetDateTime.now());
        profile = nurseProfileRepository.save(profile);

        ensureSkills(profile, List.of(
                NurseSkill.NEWBORN_BASIC_CARE,
                NurseSkill.NEWBORN_BATHING,
                NurseSkill.POSTPARTUM_RECOVERY_MASSAGE,
                NurseSkill.LACTATION_STIMULATION,
                NurseSkill.PARENT_COMMUNICATION,
                NurseSkill.SCHEDULE_MANAGEMENT));
        ensureKyc(profile, status == NurseStatus.PENDING_REVIEW ? EkycStatus.REVIEW_NEEDED : EkycStatus.PASSED, doctor);
        ensureContract(profile, signedContract);
        return profile;
    }

    private void ensureSkills(NurseProfile profile, List<NurseSkill> skills) {
        OffsetDateTime now = OffsetDateTime.now();
        for (NurseSkill skill : skills) {
            NurseSkillEntity entity = nurseSkillRepository.findByNurseAndSkill(profile, skill)
                    .orElseGet(() -> NurseSkillEntity.builder().nurse(profile).skill(skill).build());
            if (entity.getVerifiedAt() == null) {
                entity.setVerifiedAt(now);
            }
            nurseSkillRepository.save(entity);
        }
    }

    private void ensureKyc(NurseProfile nurse, EkycStatus status, User reviewer) {
        NurseKyc kyc = nurseKycRepository.findByNurse(nurse)
                .orElseGet(() -> NurseKyc.builder().nurse(nurse).build());
        kyc.setCccdNumber("079" + Math.abs(nurse.getLicenseNumber().hashCode() % 1_000_000_000));
        kyc.setCccdName(nurse.getUser().getFullName());
        kyc.setCccdDob(LocalDate.now().minusYears(30));
        kyc.setCccdAddress("20 Pasteur, District 1, Ho Chi Minh");
        kyc.setCccdFrontS3Key("test/kyc/" + nurse.getLicenseNumber() + "/front.jpg");
        kyc.setCccdBackS3Key("test/kyc/" + nurse.getLicenseNumber() + "/back.jpg");
        kyc.setEkycStatus(status);
        kyc.setReviewedBy(reviewer);
        kyc.setReviewedAt(status == EkycStatus.PASSED ? OffsetDateTime.now().minusDays(7) : null);
        kyc.setReviewNote(status == EkycStatus.PASSED ? "Seeded KYC passed." : "Seeded KYC needs manual review.");
        nurseKycRepository.save(kyc);
    }

    private void ensureContract(NurseProfile nurse, boolean signed) {
        NurseContract contract = nurseContractRepository.findTopByNurseOrderByCreatedAtDesc(nurse)
                .orElseGet(() -> NurseContract.builder().nurse(nurse).contractVersion("TEST-2026-01").build());
        contract.setContractVersion("TEST-2026-01");
        contract.setStatus(signed ? NurseContractStatus.SIGNED : NurseContractStatus.PENDING);
        contract.setSignedName(signed ? nurse.getUser().getFullName() : null);
        contract.setSignerIp(signed ? "127.0.0.1" : null);
        contract.setSignerUserAgent(signed ? "Happabi Test Seeder" : null);
        contract.setSignedAt(signed ? OffsetDateTime.now().minusDays(6) : null);
        nurseContractRepository.save(contract);
    }

    private void ensureWallet(NurseProfile nurse, long balance, long depositBalance) {
        NurseWallet wallet = nurseWalletRepository.findByNurseId(nurse.getId())
                .orElseGet(() -> NurseWallet.builder().nurseId(nurse.getId()).build());
        wallet.setBalance(BigDecimal.valueOf(balance));
        wallet.setDepositBalance(BigDecimal.valueOf(depositBalance));
        nurseWalletRepository.save(wallet);

        ensureWalletTransaction(nurse, "Top up seed wallet", TransactionType.TOPUP_WALLET, balance, balance, 0, 920000000001L + Math.abs(nurse.getLicenseNumber().hashCode() % 1000));
        if (depositBalance > 0) {
            ensureWalletTransaction(nurse, "Top up seed deposit", TransactionType.TOPUP_DEPOSIT, depositBalance, 0, depositBalance, 920000000501L + Math.abs(nurse.getLicenseNumber().hashCode() % 1000));
        }
    }

    private void ensureWalletTransaction(NurseProfile nurse, String description, TransactionType type,
                                         long amount, long walletImpact, long depositImpact, long referenceId) {
        if (walletTransactionRepository.findByReferenceIdAndStatus(referenceId, TransactionStatus.SUCCESS).isPresent()) {
            return;
        }
        walletTransactionRepository.save(WalletTransaction.builder()
                .nurseId(nurse.getId())
                .transactionType(type)
                .amount(BigDecimal.valueOf(amount))
                .walletImpact(BigDecimal.valueOf(walletImpact))
                .depositImpact(BigDecimal.valueOf(depositImpact))
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .description(description)
                .build());
    }

    private ServiceOffering requireService(String code) {
        return serviceOfferingRepository.findByServiceCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing seeded service offering: " + code));
    }

    private WorkSession ensureBookingFlow(String bookingKey, User mother, NurseProfile nurse, ServiceOffering service,
                                          BookingStatus bookingStatus, WorkSessionStatus sessionStatus,
                                          OffsetDateTime startAt, String note, boolean completedChecklist) {
        Booking booking = bookingRepository.findAll().stream()
                .filter(item -> bookingKey.equals(item.getBookingKey()))
                .findFirst()
                .orElseGet(() -> Booking.builder().bookingKey(bookingKey).build());
        booking.setMother(mother);
        booking.setNurseProfile(nurse);
        booking.setServiceOffering(service);
        BookingSlot slot = ensureSlot(nurse, startAt);
        booking.setSlot(slot);
        booking.setStatus(bookingStatus);
        booking.setStartAt(startAt);
        booking.setEndAt(startAt.plusMinutes(service.getDurationMinutes() != null ? service.getDurationMinutes() : 90));
        booking.setPaymentExpiresAt(startAt.plusMinutes(15));
        booking.setGrossAmount(service.getGrossAmount());
        booking.setPlatformFeeAmount(service.getPlatformFeeAmount());
        booking.setNurseEarningAmount(service.getNurseEarningAmount());
        booking.setPaymentOption(BookingPaymentOption.DEPOSIT_30_PERCENT);
        booking.setDepositAmount(Math.round(service.getGrossAmount() * 0.3d));
        booking.setRemainingCashAmount(service.getGrossAmount() - booking.getDepositAmount());
        booking.setAppPaymentAmount(booking.getDepositAmount());
        booking.setServiceAddress("12 Nguyen Hue, District 1, Ho Chi Minh");
        booking.setMotherNote(note);
        booking = bookingRepository.save(booking);
        slot.setStatus(BookingSlotStatus.BOOKED);
        slot.setBooking(booking);

        Booking savedBooking = booking;
        var bookingId = savedBooking.getId();
        WorkSession session = workSessionRepository.findByBooking_Id(bookingId)
                .orElseGet(() -> WorkSession.builder().booking(savedBooking).build());
        session.setMother(mother);
        session.setNurseProfile(nurse);
        session.setServiceOffering(service);
        session.setStatus(sessionStatus);
        session.setScheduledStartAt(booking.getStartAt());
        session.setScheduledEndAt(booking.getEndAt());
        applySessionTimeline(session, sessionStatus);
        session = workSessionRepository.save(session);

        ensureChecklist(session, completedChecklist || sessionStatus == WorkSessionStatus.COMPLETED
                || sessionStatus == WorkSessionStatus.PENDING_MOTHER_CONFIRMATION);
        return session;
    }

    private BookingSlot ensureSlot(NurseProfile nurse, OffsetDateTime startAt) {
        bookingSlotRepository.insertIfAbsent(UUID.randomUUID(), nurse.getId(), startAt);
        return bookingSlotRepository.findByNurseProfileIdAndStartAtForUpdate(nurse.getId(), startAt)
                .orElseThrow(() -> new IllegalStateException("Unable to create booking slot"));
    }

    private void applySessionTimeline(WorkSession session, WorkSessionStatus status) {
        OffsetDateTime start = session.getScheduledStartAt();
        if (status == WorkSessionStatus.IN_PROGRESS) {
            session.setCheckedInAt(start.plusMinutes(2));
            session.setLateMinutes(2);
            session.setCheckedOutAt(null);
        } else if (status == WorkSessionStatus.PENDING_MOTHER_CONFIRMATION || status == WorkSessionStatus.COMPLETED) {
            session.setCheckedInAt(start);
            session.setCheckedOutAt(session.getScheduledEndAt());
            session.setLateMinutes(0);
            session.setAutoConfirmAt(session.getScheduledEndAt().plusHours(48));
            session.setConfirmedAt(status == WorkSessionStatus.COMPLETED ? session.getScheduledEndAt().plusHours(2) : null);
        } else if (status == WorkSessionStatus.CANCELLED) {
            session.setCheckedInAt(null);
            session.setCheckedOutAt(null);
            session.setLateMinutes(0);
            session.setReportReason("Seeded cancelled session.");
        } else {
            session.setCheckedInAt(null);
            session.setCheckedOutAt(null);
            session.setLateMinutes(0);
        }
    }

    private void ensureChecklist(WorkSession session, boolean completed) {
        List<String> titles = List.of("Check-in with mother", "Newborn care", "Mother guidance", "Clean up and handover");
        for (int i = 0; i < titles.size(); i++) {
            int sortOrder = i + 1;
            WorkSessionChecklistItem item = checklistItemRepository.findAll().stream()
                    .filter(existing -> existing.getWorkSession().getId().equals(session.getId())
                            && existing.getSortOrder().equals(sortOrder))
                    .findFirst()
                    .orElseGet(() -> WorkSessionChecklistItem.builder()
                            .workSession(session)
                            .sortOrder(sortOrder)
                            .build());
            item.setTitle(titles.get(i));
            item.setStatus(completed ? WorkSessionChecklistStatus.COMPLETED : WorkSessionChecklistStatus.PENDING);
            item.setCompletedAt(completed ? session.getScheduledEndAt().minusMinutes(10L * (titles.size() - i)) : null);
            item.setNote(completed ? "Seeded completed checklist item." : null);
            checklistItemRepository.save(item);
        }
    }

    private void ensureReview(WorkSession session, int rating, String comment) {
        if (nurseReviewRepository.existsByWorkSession_Id(session.getId())) {
            return;
        }
        nurseReviewRepository.save(NurseReview.builder()
                .workSession(session)
                .nurseProfile(session.getNurseProfile())
                .mother(session.getMother())
                .rating(rating)
                .comment(comment)
                .tags(List.of(
                        NurseReviewTag.ON_TIME,
                        NurseReviewTag.GENTLE_WITH_BABY,
                        NurseReviewTag.CLEAR_COMMUNICATION,
                        NurseReviewTag.WOULD_BOOK_AGAIN))
                .build());
    }

    private void ensureNotification(User user, NotificationType type, String title, String message,
                                    String resourceType, String resourceId, boolean read) {
        boolean exists = notificationRepository.findTop30ByUserOrderByCreatedAtDesc(user).stream()
                .anyMatch(item -> type == item.getType()
                        && title.equals(item.getTitle())
                        && resourceId.equals(item.getResourceId()));
        if (exists) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .readAt(read ? OffsetDateTime.now().minusHours(2) : null)
                .build());
    }

    private record TestAccount(String phone, String email, String fullName, UserRole role) {
    }
}
