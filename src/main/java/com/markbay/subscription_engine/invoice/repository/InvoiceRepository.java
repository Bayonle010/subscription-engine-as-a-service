package com.markbay.subscription_engine.invoice.repository;

import com.markbay.subscription_engine.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByBillingReference(String billingReference);

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "checkoutSession"
    })
    Optional<Invoice> findByCheckoutSession_Id(UUID checkoutSessionId);

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "checkoutSession"
    })
    Optional<Invoice> findByIdAndTenant_Id(UUID invoiceId, UUID tenantId);

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription"
    })
    List<Invoice> findAllByTenant_IdAndSubscription_IdOrderByCreatedAtDesc(
            UUID tenantId,
            UUID subscriptionId
    );
}