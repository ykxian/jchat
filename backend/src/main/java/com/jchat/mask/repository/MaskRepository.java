package com.jchat.mask.repository;

import com.jchat.mask.entity.Mask;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaskRepository extends JpaRepository<Mask, Long> {

    @Query("""
            select m from Mask m
            where (
                    (:mineOnly = true and m.ownerId = :userId)
                    or (
                        :mineOnly = false and (
                            m.ownerId is null
                            or m.ownerId = :userId
                            or m.isPublic = true
                        )
                    )
            )
              and (
                    :query is null
                    or lower(m.name) like concat('%', lower(:query), '%')
                    or exists (
                        select 1
                        from unnest(m.tags) as tag
                        where lower(tag) like concat('%', lower(:query), '%')
                    )
              )
            order by m.createdAt desc, m.id desc
            """)
    List<Mask> findFirstPage(
            @Param("userId") Long userId,
            @Param("mineOnly") boolean mineOnly,
            Pageable pageable
    );

    @Query("""
            select m from Mask m
            where (
                    (:mineOnly = true and m.ownerId = :userId)
                    or (
                        :mineOnly = false and (
                            m.ownerId is null
                            or m.ownerId = :userId
                            or m.isPublic = true
                        )
                    )
            )
              and (
                    m.createdAt < :cursorCreatedAt
                    or (m.createdAt = :cursorCreatedAt and m.id < :cursorId)
              )
            order by m.createdAt desc, m.id desc
            """)
    List<Mask> findPageAfter(
            @Param("userId") Long userId,
            @Param("mineOnly") boolean mineOnly,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
