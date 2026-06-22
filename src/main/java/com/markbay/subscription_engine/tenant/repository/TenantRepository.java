package com.markbay.subscription_engine.tenant.repository;

import com.markbay.subscription_engine.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByBusinessEmailIgnoreCase(String businessEmail);

    Optional<Tenant> findByBusinessEmailIgnoreCase(String businessEmail);
}