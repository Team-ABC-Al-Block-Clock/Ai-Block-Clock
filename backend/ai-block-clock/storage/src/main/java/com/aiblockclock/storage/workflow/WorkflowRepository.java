package com.aiblockclock.storage.workflow;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {
    @Query("""
            select w from WorkflowEntity w
            where w.ownerId = :ownerId
              and (:keyword is null or w.name like concat('%', :keyword, '%'))
            order by w.lastExecutedAt desc nulls last, w.createdAt desc, w.id desc
            """)
    List<WorkflowEntity> findMineOrderByLastExecuted(@Param("ownerId") Long ownerId,
                                                     @Param("keyword") String keyword,
                                                     Pageable pageable);

    @Query("""
            select w from WorkflowEntity w
            where w.ownerId = :ownerId
              and (:keyword is null or w.name like concat('%', :keyword, '%'))
            order by w.createdAt desc, w.id desc
            """)
    List<WorkflowEntity> findMineOrderByLatest(@Param("ownerId") Long ownerId,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);
}
