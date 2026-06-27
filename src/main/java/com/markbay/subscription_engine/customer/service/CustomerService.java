package com.markbay.subscription_engine.customer.service;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import com.markbay.subscription_engine.tenant.entity.Tenant;

public interface CustomerService {

    Customer findOrCreateForCheckout(
            Tenant tenant,
            SubscriptionCheckoutSession checkoutSession
    );
}