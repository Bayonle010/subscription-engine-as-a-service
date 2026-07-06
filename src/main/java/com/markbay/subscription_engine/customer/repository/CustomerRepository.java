package com.markbay.subscription_engine.customer.repository;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.customer.enums.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByTenant_IdAndEmailIgnoreCase(
            UUID tenantId,
            String email
    );

    Page<Customer> findAllByTenant_Id(
            UUID tenantId,
            Pageable pageable
    );

    Page<Customer> findAllByTenant_IdAndStatus(
            UUID tenantId,
            CustomerStatus status,
            Pageable pageable
    );

    Optional<Customer> findByIdAndTenant_Id(
            UUID customerId,
            UUID tenantId
    );

    long countByTenant_Id(UUID tenantId);

    long countByTenant_IdAndStatus(
            UUID tenantId,
            CustomerStatus status
    );
}