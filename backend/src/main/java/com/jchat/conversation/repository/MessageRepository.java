package com.jchat.conversation.repository;

import com.jchat.conversation.entity.Message;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
              and (
                    :cursorCreatedAt is null
                    or m.createdAt > :cursorCreatedAt
                    or (m.createdAt = :cursorCreatedAt and m.id > :cursorId)
              )
            order by m.createdAt asc, m.id asc
            """)
    List<Message> findPage(
            @Param("conversationId") Long conversationId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
