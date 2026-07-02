package com.markbay.subscription_engine.nomba.support;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class NombaMoneyFormatter {

    public String toCheckoutAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        return amount
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}