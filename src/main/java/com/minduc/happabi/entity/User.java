package com.minduc.happabi.entity;

import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "cognito_username", length = 128, unique = true)
    private String cognitoUsername;

    @Column(name = "cognito_sub", length = 64, unique = true)
    private String cognitoSub;

    @Column(name = "phone", length = 20, unique = true)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "email", length = 150, unique = true)
    private String email;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "avatar_s3_key", length = 500)
    private String avatarS3Key;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserRoleAssignment> roleAssignments = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserIdentityProvider> identityProviders = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Optional<UserIdentityProvider> getIdentityProvider(AuthProvider provider) {
        return identityProviders.stream()
                .filter(p -> p.getProvider() == provider)
                .findFirst();
    }

    public boolean hasProvider(AuthProvider provider) {
        return identityProviders.stream()
                .anyMatch(p -> p.getProvider() == provider);
    }

    public Optional<String> getProviderUid(AuthProvider provider) {
        return getIdentityProvider(provider)
                .map(UserIdentityProvider::getProviderUid);
    }

    public List<Role> getRoles() {
        return roleAssignments.stream()
                .map(UserRoleAssignment::getRole)
                .toList();
    }

    public boolean hasRole(UserRole roleName) {
        return roleAssignments.stream()
                .map(UserRoleAssignment::getRole)
                .anyMatch(role -> role.getRoleName() == roleName);
    }

    public void addRoleAssignment(Role role) {
        if (hasRole(role.getRoleName())) {
            return;
        }
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .user(this)
                .role(role)
                .build();
        roleAssignments.add(assignment);
    }

    public void addOrUpdateIdentityProvider(AuthProvider provider, String providerUid) {
        Optional<UserIdentityProvider> existing = getIdentityProvider(provider);
        if (existing.isPresent()) {
            existing.get().setProviderUid(providerUid);
            return;
        }

        UserIdentityProvider link = UserIdentityProvider.builder()
                .user(this)
                .provider(provider)
                .providerUid(providerUid)
                .build();
        identityProviders.add(link);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", phone='" + phone + '\'' +
                ", email='" + email + '\'' +
                ", isActive=" + isActive +
                ", lastLoginAt=" + lastLoginAt +
                ", createdAt=" + createdAt +
                '}';
    }
}
