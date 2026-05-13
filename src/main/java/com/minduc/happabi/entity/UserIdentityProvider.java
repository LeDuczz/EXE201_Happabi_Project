package com.minduc.happabi.entity;

import com.minduc.happabi.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores the link between a User account and an external Identity Provider.
 * One User can have multiple providers: LOCAL, GOOGLE, FACEBOOK.
 *
 * - provider_uid: the cognitoSub returned by that specific provider
 * - UNIQUE(provider, provider_uid): one cognitoSub belongs to exactly one user
 * - UNIQUE(user_id, provider): one user can link each provider only once
 */
@Entity
@Table(
        name = "user_identity_providers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_provider_uid",  columnNames = {"provider", "provider_uid"}),
                @UniqueConstraint(name = "uq_user_provider", columnNames = {"user_id",  "provider"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIdentityProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    /**
     * The cognitoSub returned by this provider.
     * LOCAL    → sub from Cognito User Pool (phone-based user)
     * GOOGLE   → sub from Cognito Federated Identity (Google)
     * FACEBOOK → sub from Cognito Federated Identity (Facebook)
     */
    @Column(name = "provider_uid", nullable = false, length = 255)
    private String providerUid;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private OffsetDateTime linkedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserIdentityProvider other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UserIdentityProvider{" +
                "id=" + id +
                ", provider=" + provider +
                ", linkedAt=" + linkedAt +
                '}';
    }
}