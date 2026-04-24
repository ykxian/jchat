package com.jchat.conversation.repository;

import com.jchat.conversation.entity.Conversation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select c from Conversation c
            where c.userId = :userId
              and (:archived is null or c.archived = :archived)
              and (:pinned is null or c.pinned = :pinned)
            order by c.updatedAt desc, c.id desc
            """)
    List<Conversation> findFirstPage(
            @Param("userId") Long userId,
            @Param("archived") Boolean archived,
            @Param("pinned") Boolean pinned,
            Pageable pageable
    );

    @Query("""
            select c from Conversation c
            where c.userId = :userId
              and (:archived is null or c.archived = :archived)
              and (:pinned is null or c.pinned = :pinned)
              and (
                    c.updatedAt < :cursorUpdatedAt
                    or (c.updatedAt = :cursorUpdatedAt and c.id < :cursorId)
              )
            order by c.updatedAt desc, c.id desc
            """)
    List<Conversation> findPageAfter(
            @Param("userId") Long userId,
            @Param("archived") Boolean archived,
            @Param("pinned") Boolean pinned,
            @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
