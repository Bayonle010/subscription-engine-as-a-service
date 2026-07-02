package com.markbay.subscription_engine.customerportal.service;

import com.markbay.subscription_engine.customerportal.dto.PaymentMethodUpdateRequestResponse;

public interface PaymentMethodUpdateRequestService {

    PaymentMethodUpdateRequestResponse requestPaymentMethodUpdate(
            String rawToken
    );

    PaymentMethodUpdateRequestResponse cancelPaymentMethodUpdateRequest(
            String rawToken
    );
}