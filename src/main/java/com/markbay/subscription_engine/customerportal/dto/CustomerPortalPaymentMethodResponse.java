package com.markbay.subscription_engine.customerportal.dto;

import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;

import java.util.UUID;

public record CustomerPortalPaymentMethodResponse(
        UUID paymentMethodId,
        String type,
        String status,
        String provider,
        String cardBrand,
        String cardLast4,
        String expiryMonth,
        String expiryYear,
        boolean reusable
) {
    public static CustomerPortalPaymentMethodResponse from(
            CustomerPaymentMethod paymentMethod
    ) {
        if (paymentMethod == null) {
            return null;
        }

        return new CustomerPortalPaymentMethodResponse(
                paymentMethod.getId(),
                paymentMethod.getType() == null ? null : paymentMethod.getType().name(),
                paymentMethod.getStatus() == null ? null : paymentMethod.getStatus().name(),
                paymentMethod.getProvider(),
                paymentMethod.getCardBrand(),
                paymentMethod.getCardLast4(),
                paymentMethod.getExpiryMonth(),
                paymentMethod.getExpiryYear(),
                paymentMethod.isReusable()
        );
    }
}