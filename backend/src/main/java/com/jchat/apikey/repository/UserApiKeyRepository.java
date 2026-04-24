package com.jchat.apikey.repository;

import com.jchat.apikey.entity.UserApiKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long> {

    List<UserApiKey> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    List<UserApiKey> findByUserIdAndProviderAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, String provider);

    Optional<UserApiKey> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
