package com.markbay.subscription_engine.invoice.repository;

import com.markbay.subscription_engine.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "checkoutSession"
    })
    Optional<Invoice> findByCheckoutSession_Id(UUID checkoutSessionId);
}