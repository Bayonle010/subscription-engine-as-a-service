package com.markbay.subscription_engine.customer.repository;

import com.markbay.subscription_engine.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByTenant_IdAndEmailIgnoreCase(
            UUID tenantId,
            String email
    );
}