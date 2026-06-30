package com.markbay.subscription_engine.customerportal.repository;

import com.markbay.subscription_engine.customerportal.entity.PaymentRescueCheckoutSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRescueCheckoutSessionRepository
        extends JpaRepository<PaymentRescueCheckoutSession, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "invoice",
            "dunningCase",
            "portalSession"
    })
    Optional<PaymentRescueCheckoutSession> findByOrderReference(String orderReference);
}