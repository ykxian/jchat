package com.jchat.file.repository;

import com.jchat.file.entity.FileEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileRepository extends JpaRepository<FileEntity, Long> {

    Optional<FileEntity> findByIdAndUserId(Long id, Long userId);

    Optional<FileEntity> findByUserIdAndSha256(Long userId, String sha256);

    @Query("""
            select f from FileEntity f
            where f.userId = :userId
              and f.id in :ids
            """)
    List<FileEntity> findAllByUserIdAndIdIn(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Query("""
            select f from FileEntity f
            where f.userId = :userId
              and (:conversationId is null or f.conversationId = :conversationId)
            order by f.createdAt desc, f.id desc
            """)
    List<FileEntity> findFirstPage(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            Pageable pageable
    );

    @Query("""
            select f from FileEntity f
            where f.userId = :userId
              and (:conversationId is null or f.conversationId = :conversationId)
              and (
                    f.createdAt < :cursorCreatedAt
                    or (f.createdAt = :cursorCreatedAt and f.id < :cursorId)
              )
            order by f.createdAt desc, f.id desc
            """)
    List<FileEntity> findPageAfter(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
