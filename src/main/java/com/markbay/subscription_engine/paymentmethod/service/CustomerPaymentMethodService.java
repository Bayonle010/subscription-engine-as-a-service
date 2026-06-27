package com.markbay.subscription_engine.paymentmethod.service;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.tenant.entity.Tenant;

public interface CustomerPaymentMethodService {

    CustomerPaymentMethod findOrCreateCardPaymentMethod(
            Tenant tenant,
            Customer customer,
            NombaWebhookPaymentData paymentData,
            String providerRawData
    );
}