package com.aiblockclock.storage.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedAtEntity {

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
