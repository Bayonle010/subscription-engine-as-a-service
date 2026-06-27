package com.markbay.subscription_engine.ledger.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.ledger.dto.LedgerAccountResponse;
import com.markbay.subscription_engine.ledger.dto.LedgerBalanceResponse;
import com.markbay.subscription_engine.ledger.entity.LedgerAccount;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountStatus;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountType;
import com.markbay.subscription_engine.ledger.repository.LedgerAccountRepository;
import com.markbay.subscription_engine.ledger.service.LedgerService;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class LedgerServiceImpl implements LedgerService {

    private final LedgerAccountRepository ledgerAccountRepository;

    @Override
    @Transactional
    public List<LedgerAccount> ensureTenantLedgerAccounts(
            Tenant tenant,
            String currency
    ) {
        String resolvedCurrency = resolveCurrency(currency);

        Arrays.stream(LedgerAccountType.values())
                .forEach(type -> createIfMissing(tenant, type, resolvedCurrency));

        return ledgerAccountRepository.findAllByTenant_IdOrderByCreatedAtAsc(
                tenant.getId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAccountResponse> listTenantLedgerAccounts(UUID tenantId) {
        return ledgerAccountRepository.findAllByTenant_IdOrderByCreatedAtAsc(tenantId)
                .stream()
                .map(LedgerAccountResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerBalanceResponse getTenantMerchantBalance(
            UUID tenantId,
            String currency
    ) {
        String resolvedCurrency = resolveCurrency(currency);

        LedgerAccount merchantPayableAccount = ledgerAccountRepository
                .findByTenant_IdAndTypeAndCurrency(
                        tenantId,
                        LedgerAccountType.MERCHANT_PAYABLE,
                        resolvedCurrency
                )
                .orElseThrow(() -> new BadRequestException(
                        "Tenant ledger setup is not complete"
                ));

        return LedgerBalanceResponse.from(merchantPayableAccount);
    }

    private void createIfMissing(
            Tenant tenant,
            LedgerAccountType type,
            String currency
    ) {
        boolean exists = ledgerAccountRepository.existsByTenant_IdAndTypeAndCurrency(
                tenant.getId(),
                type,
                currency
        );

        if (exists) {
            return;
        }

        LedgerAccount ledgerAccount = LedgerAccount.builder()
                .tenant(tenant)
                .code(buildLedgerCode(tenant.getId(), type, currency))
                .name(buildLedgerName(type))
                .type(type)
                .currency(currency)
                .status(LedgerAccountStatus.ACTIVE)
                .build();

        ledgerAccountRepository.save(ledgerAccount);
    }

    private String buildLedgerCode(
            UUID tenantId,
            LedgerAccountType type,
            String currency
    ) {
        return "tenant_"
                + tenantId.toString().replace("-", "")
                + "_"
                + type.name().toLowerCase()
                + "_"
                + currency.toLowerCase();
    }

    private String buildLedgerName(LedgerAccountType type) {
        return switch (type) {
            case CASH_CLEARING -> "Cash Clearing";
            case MERCHANT_PAYABLE -> "Merchant Payable";
            case MERCHANT_REVENUE -> "Merchant Revenue";
            case PLATFORM_FEE -> "Platform Fee";
            case PAYOUT_SETTLEMENT -> "Payout Settlement";
        };
    }

    private String resolveCurrency(String currency) {
        if (currency == null || currency.trim().isBlank()) {
            return "NGN";
        }

        return currency.trim().toUpperCase();
    }
}