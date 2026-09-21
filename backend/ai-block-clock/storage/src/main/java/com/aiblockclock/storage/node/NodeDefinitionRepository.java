package com.aiblockclock.storage.node;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeDefinitionRepository extends JpaRepository<NodeDefinitionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NodeDefinitionEntity n where n.id = :id")
    Optional<NodeDefinitionEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select n from NodeDefinitionEntity n where n.id = :id")
    Optional<NodeDefinitionEntity> findByIdForShare(@Param("id") UUID id);

    @Query("""
            select n from NodeDefinitionEntity n
            where n.ownerId = :ownerId
              and (:keyword is null or n.name like concat('%', :keyword, '%'))
            order by n.createdAt desc, n.id desc
            """)
    List<NodeDefinitionEntity> findMineOrderByLatest(@Param("ownerId") Long ownerId,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);

    boolean existsByIdAndOwnerId(UUID id, Long ownerId);
}
