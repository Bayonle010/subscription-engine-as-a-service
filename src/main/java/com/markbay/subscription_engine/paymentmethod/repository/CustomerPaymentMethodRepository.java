package com.markbay.subscription_engine.paymentmethod.repository;

import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerPaymentMethodRepository
        extends JpaRepository<CustomerPaymentMethod, UUID> {

    Optional<CustomerPaymentMethod> findByTenant_IdAndProviderTokenKey(
            UUID tenantId,
            String providerTokenKey
    );
}