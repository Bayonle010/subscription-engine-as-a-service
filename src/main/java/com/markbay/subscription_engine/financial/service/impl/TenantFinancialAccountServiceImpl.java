package com.markbay.subscription_engine.financial.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ConflictException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.financial.dto.SetupTenantFinancialAccountRequest;
import com.markbay.subscription_engine.financial.dto.TenantFinancialAccountBalanceResponse;
import com.markbay.subscription_engine.financial.dto.TenantFinancialAccountResponse;
import com.markbay.subscription_engine.financial.dto.TenantFinancialSetupResponse;
import com.markbay.subscription_engine.financial.entity.TenantFinancialAccount;
import com.markbay.subscription_engine.financial.enums.FinancialAccountStatus;
import com.markbay.subscription_engine.financial.enums.FinancialProvider;
import com.markbay.subscription_engine.financial.repository.TenantFinancialAccountRepository;
import com.markbay.subscription_engine.financial.service.TenantFinancialAccountService;
import com.markbay.subscription_engine.ledger.dto.LedgerAccountResponse;
import com.markbay.subscription_engine.ledger.entity.LedgerAccount;
import com.markbay.subscription_engine.ledger.service.LedgerService;
import com.markbay.subscription_engine.nomba.dto.response.NombaSubAccountBalanceResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaSubAccountResult;
import com.markbay.subscription_engine.nomba.gateway.NombaSubAccountGateway;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class TenantFinancialAccountServiceImpl
        implements TenantFinancialAccountService {

    private final TenantFinancialAccountRepository financialAccountRepository;
    private final TenantRepository tenantRepository;
    private final LedgerService ledgerService;
    private final NombaSubAccountGateway nombaSubAccountGateway;
    private final AuthenticatedTenantProvider authenticatedTenantProvider;
    @Value("${payment.nomba.sub-account.enabled:false}")
    private boolean nombaSubAccountEnabled;


    @Override
    @Transactional
    public TenantFinancialSetupResponse setupFinancialAccountForTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        TenantFinancialAccount financialAccount =
                financialAccountRepository.findByTenant_Id(tenantId)
                        .orElseGet(() -> buildPendingFinancialAccount(tenant));

        if (financialAccount.getStatus() == FinancialAccountStatus.ACTIVE) {
            List<LedgerAccountResponse> ledgerAccounts =
                    ledgerService.listTenantLedgerAccounts(tenantId);

            return new TenantFinancialSetupResponse(
                    TenantFinancialAccountResponse.from(financialAccount),
                    ledgerAccounts
            );
        }

        if (financialAccount.getStatus() == FinancialAccountStatus.DISABLED) {
            throw new BadRequestException("Tenant financial account is disabled");
        }

        String accountName = resolveAccountName(tenant, financialAccount);
        String accountRef = resolveAccountRef(tenant, financialAccount);

        financialAccount.setProvider(FinancialProvider.NOMBA);
        financialAccount.setAccountName(accountName);
        financialAccount.setAccountRef(accountRef);
        financialAccount.setStatus(FinancialAccountStatus.PENDING);
        financialAccount.setFailureReason(null);

        financialAccountRepository.save(financialAccount);

        if (!nombaSubAccountEnabled) {
            return completeInternalLedgerOnlySetup(
                    tenant,
                    financialAccount,
                    accountName,
                    accountRef
            );
        }

        return completeNombaSubAccountSetup(
                tenant,
                financialAccount,
                accountName,
                accountRef
        );
    }

    @Override
    @Transactional
    public TenantFinancialSetupResponse retryFinancialSetupForCurrentTenant() {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        return setupFinancialAccountForTenant(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantFinancialAccountResponse getCurrentTenantFinancialAccount() {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        TenantFinancialAccount financialAccount =
                financialAccountRepository.findByTenant_Id(tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Tenant financial account not found"
                        ));

        return TenantFinancialAccountResponse.from(financialAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantFinancialAccountBalanceResponse getCurrentTenantSubAccountBalance() {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        if (!nombaSubAccountEnabled) {
            throw new BadRequestException("Nomba sub-account balance is not available in internal ledger mode");
        }

        TenantFinancialAccount financialAccount =
                financialAccountRepository.findByTenant_Id(tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Tenant financial account not found"
                        ));

        if (financialAccount.getStatus() != FinancialAccountStatus.ACTIVE) {
            throw new BadRequestException("Tenant financial account is not active");
        }

        if (!hasText(financialAccount.getProviderAccountId())) {
            throw new BadRequestException("Nomba sub-account ID is missing");
        }

        NombaSubAccountBalanceResult balance =
                nombaSubAccountGateway.fetchSubAccountBalance(
                        financialAccount.getProviderAccountId()
                );

        return new TenantFinancialAccountBalanceResponse(
                financialAccount.getTenant().getId(),
                financialAccount.getTenant().getId(),
                financialAccount.getProviderAccountId(),
                financialAccount.getAccountRef(),
                balance.availableBalance(),
                balance.currency(),
                balance.rawResponse()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TenantFinancialAccount requireActiveFinancialAccount(UUID tenantId) {
        TenantFinancialAccount financialAccount =
                financialAccountRepository.findByTenant_Id(tenantId)
                        .orElseThrow(() -> new BadRequestException(
                                "Tenant financial setup is not complete"
                        ));

        if (financialAccount.getStatus() != FinancialAccountStatus.ACTIVE) {
            throw new BadRequestException("Tenant financial setup is not complete");
        }

        if (nombaSubAccountEnabled && !hasText(financialAccount.getProviderAccountId())) {
            throw new BadRequestException("Tenant Nomba sub-account is not configured");
        }

        return financialAccount;
    }

    private TenantFinancialAccount buildPendingFinancialAccount(Tenant tenant) {
        return TenantFinancialAccount.builder()
                .tenant(tenant)
                .provider(FinancialProvider.NOMBA)
                .accountName(resolveAccountName(tenant, null))
                .accountRef(resolveAccountRef(tenant, null))
                .status(FinancialAccountStatus.PENDING)
                .build();
    }

    private TenantFinancialSetupResponse completeInternalLedgerOnlySetup(
            Tenant tenant,
            TenantFinancialAccount financialAccount,
            String accountName,
            String accountRef
    ) {
        financialAccount.setProvider(FinancialProvider.NOMBA);
        financialAccount.setProviderAccountId(null);
        financialAccount.setAccountName(accountName);
        financialAccount.setAccountRef(accountRef);
        financialAccount.setProviderRawResponse("Nomba sub-account creation skipped. Internal ledger mode is active.");
        financialAccount.setStatus(FinancialAccountStatus.ACTIVE);
        financialAccount.setSetupCompletedAt(Instant.now());
        financialAccount.setFailureReason(null);

        TenantFinancialAccount savedFinancialAccount =
                financialAccountRepository.save(financialAccount);

        List<LedgerAccount> ledgerAccounts =
                ledgerService.ensureTenantLedgerAccounts(
                        tenant,
                        tenant.getDefaultCurrency()
                );

        List<LedgerAccountResponse> ledgerAccountResponses =
                ledgerAccounts.stream()
                        .map(LedgerAccountResponse::from)
                        .toList();

        return new TenantFinancialSetupResponse(
                TenantFinancialAccountResponse.from(savedFinancialAccount),
                ledgerAccountResponses
        );
    }

    private TenantFinancialSetupResponse completeNombaSubAccountSetup(
            Tenant tenant,
            TenantFinancialAccount financialAccount,
            String accountName,
            String accountRef
    ) {
        try {
            NombaSubAccountResult nombaSubAccount =
                    nombaSubAccountGateway.createSubAccount(accountName, accountRef);

            financialAccount.setProviderAccountId(nombaSubAccount.providerAccountId());
            financialAccount.setAccountName(nombaSubAccount.accountName());
            financialAccount.setAccountRef(nombaSubAccount.accountRef());
            financialAccount.setProviderRawResponse(nombaSubAccount.rawResponse());
            financialAccount.setStatus(FinancialAccountStatus.ACTIVE);
            financialAccount.setSetupCompletedAt(Instant.now());
            financialAccount.setFailureReason(null);

            TenantFinancialAccount savedFinancialAccount =
                    financialAccountRepository.save(financialAccount);

            List<LedgerAccount> ledgerAccounts =
                    ledgerService.ensureTenantLedgerAccounts(
                            tenant,
                            tenant.getDefaultCurrency()
                    );

            List<LedgerAccountResponse> ledgerAccountResponses =
                    ledgerAccounts.stream()
                            .map(LedgerAccountResponse::from)
                            .toList();

            return new TenantFinancialSetupResponse(
                    TenantFinancialAccountResponse.from(savedFinancialAccount),
                    ledgerAccountResponses
            );

        } catch (RuntimeException exception) {
            financialAccount.setStatus(FinancialAccountStatus.FAILED);
            financialAccount.setFailureReason(exception.getMessage());

            TenantFinancialAccount savedFinancialAccount =
                    financialAccountRepository.save(financialAccount);

            List<LedgerAccountResponse> ledgerAccounts =
                    ledgerService.listTenantLedgerAccounts(tenant.getId());

            return new TenantFinancialSetupResponse(
                    TenantFinancialAccountResponse.from(savedFinancialAccount),
                    ledgerAccounts
            );
        }
    }

    private String resolveAccountName(
            Tenant tenant,
            TenantFinancialAccount existingAccount
    ) {
        if (existingAccount != null && hasText(existingAccount.getAccountName())) {
            return existingAccount.getAccountName();
        }

        return tenant.getBusinessName();
    }

    private String resolveAccountRef(
            Tenant tenant,
            TenantFinancialAccount existingAccount
    ) {
        if (existingAccount != null && hasText(existingAccount.getAccountRef())) {
            return existingAccount.getAccountRef();
        }

        return "tenant_" + tenant.getId().toString().replace("-", "");
    }

    private String resolveAccountRef(
            SetupTenantFinancialAccountRequest request,
            Tenant tenant,
            TenantFinancialAccount existingAccount
    ) {
        if (existingAccount != null && hasText(existingAccount.getAccountRef())) {
            return existingAccount.getAccountRef();
        }

        if (request != null && hasText(request.accountRef())) {
            return request.accountRef().trim();
        }

        return "tenant_" + tenant.getId().toString().replace("-", "");
    }


    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}