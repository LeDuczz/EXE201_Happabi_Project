package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.dto.request.admin.CreateDoctorAccountRequest;
import com.minduc.happabi.dto.response.admin.DoctorAccountResponse;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.integration.cognito.CognitoService;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.admin.IAdminDoctorAccountService;
import com.minduc.happabi.service.auth.UserProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDoctorAccountServiceImpl implements IAdminDoctorAccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserProviderService userProviderService;
    private final CognitoService cognitoService;

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DOCTOR:CREATE')")
    @TimedAction("ADMIN_CREATE_DOCTOR_ACCOUNT")
    @LogExecution
    @AuditAction(action = "ADMIN_CREATE_DOCTOR_ACCOUNT", resourceType = "DOCTOR_ACCOUNT")
    public DoctorAccountResponse createDoctorAccount(CreateDoctorAccountRequest request) {
        String fullName = request.getFullName().trim();
        String phone = request.getPhone().trim();
        String email = normalizeEmail(request.getEmail());

        ensureDoctorAccountAvailable(phone, email);

        Role doctorRole = roleRepository.findByRoleName(UserRole.DOCTOR)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "DOCTOR role not found."));

        String initialPassword = generateInitialPassword();
        String cognitoSub = createCognitoDoctor(phone, initialPassword, fullName, email);

        try {
            User doctor = userRepository.save(User.builder()
                    .fullName(fullName)
                    .cognitoUsername(cognitoSub)
                    .cognitoSub(cognitoSub)
                    .phone(phone)
                    .phoneVerified(true)
                    .email(email)
                    .emailVerified(email != null)
                    .isActive(true)
                    .build());

            userProviderService.saveIdentityProvider(doctor, AuthProvider.LOCAL, phone);
            userProviderService.saveRoleAssignment(doctor, doctorRole);

            log.info("Doctor account created: userId={} username={}", doctor.getId(), phone);
            return DoctorAccountResponse.builder()
                    .userId(doctor.getId())
                    .fullName(doctor.getFullName())
                    .phone(doctor.getPhone())
                    .email(doctor.getEmail())
                    .role(UserRole.DOCTOR)
                    .initialPassword(initialPassword)
                    .build();
        } catch (RuntimeException e) {
            rollbackCognitoDoctor(phone);
            throw e;
        }
    }

    private void ensureDoctorAccountAvailable(String phone, String email) {
        userRepository.findByPhone(phone).ifPresent(user -> {
            throw new AppException(AuthErrorCode.PHONE_ALREADY_EXISTS);
        });

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user -> {
                throw new AppException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
            });
        }
    }

    private String createCognitoDoctor(String phone, String password, String fullName, String email) {
        try {
            String cognitoSub = cognitoService.adminCreateUser(phone, password, fullName, email, phone);
            cognitoService.adminAddUserToGroup(phone, UserRole.DOCTOR.name());
            return cognitoSub;
        } catch (UsernameExistsException e) {
            throw new AppException(AuthErrorCode.PHONE_ALREADY_EXISTS, e);
        } catch (CognitoIdentityProviderException e) {
            log.warn("Failed to create doctor Cognito account: username={} message={}",
                    phone, e.awsErrorDetails().errorMessage());
            throw new AppException(AuthErrorCode.AUTH_FAILED, e.awsErrorDetails().errorMessage(), e);
        }
    }

    private void rollbackCognitoDoctor(String username) {
        try {
            cognitoService.adminDeleteUser(username);
            log.info("Rolled back doctor Cognito account after local persistence failure: username={}", username);
        } catch (CognitoIdentityProviderException e) {
            log.warn("Failed to rollback doctor Cognito account: username={} message={}",
                    username, e.awsErrorDetails().errorMessage());
        }
    }

    private String generateInitialPassword() {
        return "Hap@" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "9aA";
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
