package com.markbay.subscription_engine.billing.service.impl;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.billing.service.BillingFeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
public class BillingFeeServiceImpl implements BillingFeeService {

    private final BigDecimal platformFeePercentage;
    private final BigDecimal platformFeeFixedAmount;

    public BillingFeeServiceImpl(
            @Value("${billing.platform-fee.percentage:0}") BigDecimal platformFeePercentage,
            @Value("${billing.platform-fee.fixed-amount:0}") BigDecimal platformFeeFixedAmount
    ) {
        this.platformFeePercentage = platformFeePercentage;
        this.platformFeeFixedAmount = platformFeeFixedAmount;
    }

    @Override
    public BillingFeeResult calculateFee(
            BigDecimal grossAmount,
            String currency
    ) {
        BigDecimal safeGrossAmount = safeAmount(grossAmount);

        BigDecimal percentageFee = safeGrossAmount
                .multiply(safeAmount(platformFeePercentage))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        BigDecimal platformFee = percentageFee
                .add(safeAmount(platformFeeFixedAmount))
                .setScale(4, RoundingMode.HALF_UP);

        if (platformFee.compareTo(safeGrossAmount) > 0) {
            platformFee = safeGrossAmount;
        }

        BigDecimal merchantNetAmount = safeGrossAmount
                .subtract(platformFee)
                .setScale(4, RoundingMode.HALF_UP);

        log.info(
                "Billing fee calculated. grossAmount={}, platformFee={}, merchantNetAmount={}, currency={}",
                safeGrossAmount,
                platformFee,
                merchantNetAmount,
                currency
        );

        return new BillingFeeResult(
                safeGrossAmount,
                platformFee,
                merchantNetAmount,
                currency
        );
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        return amount.setScale(4, RoundingMode.HALF_UP);
    }
}