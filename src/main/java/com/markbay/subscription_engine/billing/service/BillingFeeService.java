package com.markbay.subscription_engine.billing.service;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;

import java.math.BigDecimal;

public interface BillingFeeService {

    BillingFeeResult calculateFee(
            BigDecimal grossAmount,
            String currency
    );
}