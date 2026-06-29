package com.markbay.subscription_engine.payment.repository;

import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.payment.enums.PaymentProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "invoice",
            "checkoutSession",
            "paymentMethod"
    })
    Optional<Payment> findByTenant_IdAndOrderReference(
            UUID tenantId,
            String orderReference
    );

    Optional<Payment> findByProviderAndProviderTransactionReference(
            PaymentProvider provider,
            String providerTransactionReference
    );
}