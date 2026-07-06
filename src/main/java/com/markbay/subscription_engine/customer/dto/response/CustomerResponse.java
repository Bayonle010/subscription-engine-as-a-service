package com.markbay.subscription_engine.customer.dto.response;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.customer.enums.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        UUID accountId,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        String phone,
        String externalCustomerId,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getTenant().getId(),
                customer.getTenant().getId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPhone(),
                customer.getExternalCustomerId(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}