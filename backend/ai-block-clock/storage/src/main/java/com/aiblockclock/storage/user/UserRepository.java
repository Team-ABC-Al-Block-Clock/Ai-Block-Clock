package com.aiblockclock.storage.user;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByWalletAddressIgnoreCase(String walletAddress);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT appUser FROM UserEntity appUser WHERE appUser.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);
}
