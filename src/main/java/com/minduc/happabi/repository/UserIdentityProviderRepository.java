package com.minduc.happabi.repository;

import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserIdentityProvider;
import com.minduc.happabi.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityProviderRepository extends JpaRepository<UserIdentityProvider, UUID> {

    @Query("SELECT p.user FROM UserIdentityProvider p WHERE p.provider = :provider AND p.providerUid = :providerUid")
    Optional<User> findUserByProviderAndProviderUid(@Param("provider") AuthProvider provider,
                                                    @Param("providerUid") String providerUid);

    @Query("""
        SELECT u FROM UserIdentityProvider p
        JOIN p.user u
        LEFT JOIN FETCH u.roleAssignments ra
        LEFT JOIN FETCH ra.role
        LEFT JOIN FETCH u.identityProviders
        WHERE p.providerUid = :providerUid
    """)
    Optional<User> findUserByProviderUidWithRolesAndProviders(@Param("providerUid") String providerUid);

    boolean existsByUserAndProvider(User user, AuthProvider provider);

    Optional<UserIdentityProvider> findByUserAndProvider(User user, AuthProvider provider);

    Optional<UserIdentityProvider> findByProviderUid(String providerUid);

}
