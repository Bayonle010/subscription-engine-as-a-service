package com.markbay.subscription_engine.merchant.repository;

import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantUserRepository extends JpaRepository<MerchantUser, UUID> {

    Optional<MerchantUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}