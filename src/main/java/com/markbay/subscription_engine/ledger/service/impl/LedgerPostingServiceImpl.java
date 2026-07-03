package com.markbay.subscription_engine.ledger.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.ledger.entity.LedgerAccount;
import com.markbay.subscription_engine.ledger.entity.LedgerEntry;
import com.markbay.subscription_engine.ledger.entity.LedgerTransaction;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountType;
import com.markbay.subscription_engine.ledger.enums.LedgerEntryType;
import com.markbay.subscription_engine.ledger.enums.LedgerTransactionStatus;
import com.markbay.subscription_engine.ledger.repository.LedgerAccountRepository;
import com.markbay.subscription_engine.ledger.repository.LedgerTransactionRepository;
import com.markbay.subscription_engine.ledger.service.LedgerPostingService;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerPostingServiceImpl implements LedgerPostingService {

    private static final String SOURCE_TYPE_INITIAL_SUBSCRIPTION_PAYMENT =
            "INITIAL_SUBSCRIPTION_PAYMENT";

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;

    @Override
    @Transactional
    public LedgerPostingResult postInitialSubscriptionPayment(
            Subscription subscription,
            Payment payment,
            BillingFeeResult feeResult
    ) {
        String transactionRef = buildTransactionRef(payment);

        var existingTransaction =
                ledgerTransactionRepository.findByTransactionRef(transactionRef);

        if (existingTransaction.isPresent()) {
            log.info(
                    "Initial subscription ledger posting already exists. transactionRef={}",
                    transactionRef
            );

            return new LedgerPostingResult(
                    existingTransaction.get().getId(),
                    transactionRef,
                    feeResult.grossAmount(),
                    feeResult.platformFee(),
                    feeResult.merchantNetAmount(),
                    feeResult.currency()
            );
        }

        UUID tenantId = subscription.getTenant().getId();
        String currency = feeResult.currency();

        LedgerAccount cashClearingAccount = lockLedgerAccount(
                tenantId,
                LedgerAccountType.CASH_CLEARING,
                currency
        );

        LedgerAccount merchantPayableAccount = lockLedgerAccount(
                tenantId,
                LedgerAccountType.MERCHANT_PAYABLE,
                currency
        );

        LedgerAccount platformFeeAccount = null;

        if (isPositive(feeResult.platformFee())) {
            platformFeeAccount = lockLedgerAccount(
                    tenantId,
                    LedgerAccountType.PLATFORM_FEE,
                    currency
            );
        }

        LedgerTransaction ledgerTransaction = LedgerTransaction.builder()
                .tenant(subscription.getTenant())
                .transactionRef(transactionRef)
                .sourceType(SOURCE_TYPE_INITIAL_SUBSCRIPTION_PAYMENT)
                .sourceId(payment.getId().toString())
                .description("Initial subscription payment")
                .status(LedgerTransactionStatus.POSTED)
                .build();

        LedgerEntry cashDebitEntry = LedgerEntry.builder()
                .ledgerAccount(cashClearingAccount)
                .entryType(LedgerEntryType.DEBIT)
                .amount(feeResult.grossAmount())
                .currency(currency)
                .build();

        LedgerEntry merchantPayableCreditEntry = LedgerEntry.builder()
                .ledgerAccount(merchantPayableAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(feeResult.merchantNetAmount())
                .currency(currency)
                .build();

        ledgerTransaction.addEntry(cashDebitEntry);
        ledgerTransaction.addEntry(merchantPayableCreditEntry);

        if (platformFeeAccount != null) {
            LedgerEntry platformFeeCreditEntry = LedgerEntry.builder()
                    .ledgerAccount(platformFeeAccount)
                    .entryType(LedgerEntryType.CREDIT)
                    .amount(feeResult.platformFee())
                    .currency(currency)
                    .build();

            ledgerTransaction.addEntry(platformFeeCreditEntry);
        }

        assertBalanced(ledgerTransaction);

        applyEntryToBalance(
                cashClearingAccount,
                LedgerEntryType.DEBIT,
                feeResult.grossAmount()
        );

        applyEntryToBalance(
                merchantPayableAccount,
                LedgerEntryType.CREDIT,
                feeResult.merchantNetAmount()
        );

        if (platformFeeAccount != null) {
            applyEntryToBalance(
                    platformFeeAccount,
                    LedgerEntryType.CREDIT,
                    feeResult.platformFee()
            );
        }

        LedgerTransaction savedTransaction =
                ledgerTransactionRepository.save(ledgerTransaction);

        log.info(
                "Initial subscription ledger posting completed. tenantId={}, subscriptionId={}, paymentId={}, transactionRef={}, grossAmount={}, merchantNetAmount={}, platformFee={}, currency={}",
                tenantId,
                subscription.getId(),
                payment.getId(),
                transactionRef,
                feeResult.grossAmount(),
                feeResult.merchantNetAmount(),
                feeResult.platformFee(),
                currency
        );

        return new LedgerPostingResult(
                savedTransaction.getId(),
                transactionRef,
                feeResult.grossAmount(),
                feeResult.platformFee(),
                feeResult.merchantNetAmount(),
                currency
        );
    }


    @Override
    @Transactional
    public LedgerPostingResult postRenewalSubscriptionPayment(
            Subscription subscription,
            Payment payment,
            BillingFeeResult feeResult
    ) {
        return postSubscriptionPayment(
                subscription,
                payment,
                feeResult,
                "RENEWAL_SUBSCRIPTION_PAYMENT",
                "Renewal subscription payment",
                "RENEWAL_SUB_PAYMENT:"
        );
    }

    private LedgerAccount lockLedgerAccount(
            UUID tenantId,
            LedgerAccountType type,
            String currency
    ) {
        return ledgerAccountRepository
                .findByTenantIdAndTypeAndCurrencyForUpdate(
                        tenantId,
                        type,
                        currency
                )
                .orElseThrow(() -> new BadRequestException(
                        "Tenant ledger account is missing: " + type
                ));
    }

    @Override
    @Transactional
    public LedgerPostingResult postProrationPayment(
            Subscription subscription,
            Payment payment,
            BillingFeeResult feeResult
    ) {
        return postSubscriptionPayment(
                subscription,
                payment,
                feeResult,
                "PRORATION",
                "Proration payment for plan switch",
                "PRORATION_PAYMENT:"
        );
    }

    private LedgerPostingResult postSubscriptionPayment(
            Subscription subscription,
            Payment payment,
            BillingFeeResult feeResult,
            String sourceType,
            String description,
            String transactionRefPrefix
    ) {
        String transactionRef = buildTransactionRef(payment, transactionRefPrefix);

        var existingTransaction =
                ledgerTransactionRepository.findByTransactionRef(transactionRef);

        if (existingTransaction.isPresent()) {
            log.info(
                    "Subscription ledger posting already exists. transactionRef={}",
                    transactionRef
            );

            return new LedgerPostingResult(
                    existingTransaction.get().getId(),
                    transactionRef,
                    feeResult.grossAmount(),
                    feeResult.platformFee(),
                    feeResult.merchantNetAmount(),
                    feeResult.currency()
            );
        }

        UUID tenantId = subscription.getTenant().getId();
        String currency = feeResult.currency();

        LedgerAccount cashClearingAccount = lockLedgerAccount(
                tenantId,
                LedgerAccountType.CASH_CLEARING,
                currency
        );

        LedgerAccount merchantPayableAccount = lockLedgerAccount(
                tenantId,
                LedgerAccountType.MERCHANT_PAYABLE,
                currency
        );

        LedgerAccount platformFeeAccount = null;

        if (isPositive(feeResult.platformFee())) {
            platformFeeAccount = lockLedgerAccount(
                    tenantId,
                    LedgerAccountType.PLATFORM_FEE,
                    currency
            );
        }

        LedgerTransaction ledgerTransaction = LedgerTransaction.builder()
                .tenant(subscription.getTenant())
                .transactionRef(transactionRef)
                .sourceType(sourceType)
                .sourceId(payment.getId().toString())
                .description(description)
                .status(LedgerTransactionStatus.POSTED)
                .build();

        ledgerTransaction.addEntry(
                LedgerEntry.builder()
                        .ledgerAccount(cashClearingAccount)
                        .entryType(LedgerEntryType.DEBIT)
                        .amount(feeResult.grossAmount())
                        .currency(currency)
                        .build()
        );

        ledgerTransaction.addEntry(
                LedgerEntry.builder()
                        .ledgerAccount(merchantPayableAccount)
                        .entryType(LedgerEntryType.CREDIT)
                        .amount(feeResult.merchantNetAmount())
                        .currency(currency)
                        .build()
        );

        if (platformFeeAccount != null) {
            ledgerTransaction.addEntry(
                    LedgerEntry.builder()
                            .ledgerAccount(platformFeeAccount)
                            .entryType(LedgerEntryType.CREDIT)
                            .amount(feeResult.platformFee())
                            .currency(currency)
                            .build()
            );
        }

        assertBalanced(ledgerTransaction);

        applyEntryToBalance(
                cashClearingAccount,
                LedgerEntryType.DEBIT,
                feeResult.grossAmount()
        );

        applyEntryToBalance(
                merchantPayableAccount,
                LedgerEntryType.CREDIT,
                feeResult.merchantNetAmount()
        );

        if (platformFeeAccount != null) {
            applyEntryToBalance(
                    platformFeeAccount,
                    LedgerEntryType.CREDIT,
                    feeResult.platformFee()
            );
        }

        LedgerTransaction savedTransaction =
                ledgerTransactionRepository.save(ledgerTransaction);

        log.info(
                "Subscription ledger posting completed. tenantId={}, subscriptionId={}, paymentId={}, transactionRef={}, grossAmount={}, merchantNetAmount={}, platformFee={}, currency={}",
                tenantId,
                subscription.getId(),
                payment.getId(),
                transactionRef,
                feeResult.grossAmount(),
                feeResult.merchantNetAmount(),
                feeResult.platformFee(),
                currency
        );

        return new LedgerPostingResult(
                savedTransaction.getId(),
                transactionRef,
                feeResult.grossAmount(),
                feeResult.platformFee(),
                feeResult.merchantNetAmount(),
                currency
        );
    }



    private String buildTransactionRef(
            Payment payment,
            String prefix
    ) {
        if (hasText(payment.getProviderTransactionReference())) {
            return prefix + payment.getProviderTransactionReference();
        }

        return prefix + payment.getOrderReference();
    }

    private String buildTransactionRef(Payment payment) {
        if (hasText(payment.getProviderTransactionReference())) {
            return "INITIAL_SUB_PAYMENT:" + payment.getProviderTransactionReference();
        }

        return "INITIAL_SUB_PAYMENT:" + payment.getOrderReference();
    }

    private void assertBalanced(LedgerTransaction transaction) {
        BigDecimal totalDebits = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalCredits = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (LedgerEntry entry : transaction.getEntries()) {
            BigDecimal amount = normalizeAmount(entry.getAmount());

            if (entry.getEntryType() == LedgerEntryType.DEBIT) {
                totalDebits = totalDebits.add(amount);
            }

            if (entry.getEntryType() == LedgerEntryType.CREDIT) {
                totalCredits = totalCredits.add(amount);
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new BadRequestException(
                    "Ledger transaction is not balanced"
            );
        }
    }

    private void applyEntryToBalance(
            LedgerAccount account,
            LedgerEntryType entryType,
            BigDecimal amount
    ) {
        BigDecimal currentBalance = normalizeAmount(account.getBalance());
        BigDecimal normalizedAmount = normalizeAmount(amount);

        if (isDebitNormalAccount(account.getType())) {
            if (entryType == LedgerEntryType.DEBIT) {
                account.setBalance(currentBalance.add(normalizedAmount));
            } else {
                account.setBalance(currentBalance.subtract(normalizedAmount));
            }

            return;
        }

        if (entryType == LedgerEntryType.CREDIT) {
            account.setBalance(currentBalance.add(normalizedAmount));
        } else {
            account.setBalance(currentBalance.subtract(normalizedAmount));
        }
    }

    private boolean isDebitNormalAccount(LedgerAccountType type) {
        return type == LedgerAccountType.CASH_CLEARING
                || type == LedgerAccountType.PAYOUT_SETTLEMENT;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    private boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}