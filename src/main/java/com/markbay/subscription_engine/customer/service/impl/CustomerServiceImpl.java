package com.markbay.subscription_engine.customer.service.impl;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.customer.enums.CustomerStatus;
import com.markbay.subscription_engine.customer.repository.CustomerRepository;
import com.markbay.subscription_engine.customer.service.CustomerService;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Override
    public Customer findOrCreateForCheckout(
            Tenant tenant,
            SubscriptionCheckoutSession checkoutSession
    ) {
        String email = checkoutSession.getCustomerEmail().trim().toLowerCase();

        return customerRepository.findByTenant_IdAndEmailIgnoreCase(
                        tenant.getId(),
                        email
                )
                .map(existingCustomer -> updateCustomerIfNeeded(
                        existingCustomer,
                        checkoutSession
                ))
                .orElseGet(() -> createCustomer(tenant, checkoutSession, email));
    }

    private Customer createCustomer(
            Tenant tenant,
            SubscriptionCheckoutSession checkoutSession,
            String email
    ) {
        Customer customer = Customer.builder()
                .tenant(tenant)
                .email(email)
                .firstName(checkoutSession.getCustomerFirstName())
                .lastName(checkoutSession.getCustomerLastName())
                .phone(checkoutSession.getCustomerPhone())
                .status(CustomerStatus.ACTIVE)
                .build();

        return customerRepository.save(customer);
    }

    private Customer updateCustomerIfNeeded(
            Customer customer,
            SubscriptionCheckoutSession checkoutSession
    ) {
        boolean changed = false;

        if (!hasText(customer.getFirstName()) && hasText(checkoutSession.getCustomerFirstName())) {
            customer.setFirstName(checkoutSession.getCustomerFirstName());
            changed = true;
        }

        if (!hasText(customer.getLastName()) && hasText(checkoutSession.getCustomerLastName())) {
            customer.setLastName(checkoutSession.getCustomerLastName());
            changed = true;
        }

        if (!hasText(customer.getPhone()) && hasText(checkoutSession.getCustomerPhone())) {
            customer.setPhone(checkoutSession.getCustomerPhone());
            changed = true;
        }

        if (changed) {
            return customerRepository.save(customer);
        }

        return customer;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}