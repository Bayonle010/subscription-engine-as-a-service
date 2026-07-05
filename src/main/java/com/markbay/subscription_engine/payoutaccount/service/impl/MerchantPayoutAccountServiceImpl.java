package com.markbay.subscription_engine.payoutaccount.service.impl;

import com.markbay.subscription_engine.bank.entity.NombaBank;
import com.markbay.subscription_engine.bank.enums.BankStatus;
import com.markbay.subscription_engine.bank.repository.NombaBankRepository;
import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.nomba.dto.response.NombaBankAccountLookupResult;
import com.markbay.subscription_engine.nomba.gateway.NombaBankGateway;
import com.markbay.subscription_engine.payoutaccount.dto.BankAccountLookupRequest;
import com.markbay.subscription_engine.payoutaccount.dto.BankAccountLookupResponse;
import com.markbay.subscription_engine.payoutaccount.dto.CreatePayoutAccountRequest;
import com.markbay.subscription_engine.payoutaccount.dto.MerchantPayoutAccountResponse;
import com.markbay.subscription_engine.payoutaccount.entity.MerchantPayoutAccount;
import com.markbay.subscription_engine.payoutaccount.enums.PayoutAccountStatus;
import com.markbay.subscription_engine.payoutaccount.enums.PayoutDestinationType;
import com.markbay.subscription_engine.payoutaccount.repository.MerchantPayoutAccountRepository;
import com.markbay.subscription_engine.payoutaccount.service.MerchantPayoutAccountService;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantPayoutAccountServiceImpl
        implements MerchantPayoutAccountService {

    private final AuthenticatedTenantProvider authenticatedTenantProvider;
    private final NombaBankGateway nombaBankGateway;
    private final NombaBankRepository nombaBankRepository;
    private final MerchantPayoutAccountRepository payoutAccountRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public BankAccountLookupResponse lookupBankAccount(
            BankAccountLookupRequest request
    ) {
        validateAccountNumber(request.accountNumber());

        NombaBank bank = findActiveBankByCode(request.bankCode());

        NombaBankAccountLookupResult lookupResult =
                nombaBankGateway.lookupBankAccount(
                        request.accountNumber(),
                        request.bankCode()
                );

        if (!lookupResult.success() || !hasText(lookupResult.accountName())) {
            throw new BadRequestException("Unable to verify bank account");
        }

        return new BankAccountLookupResponse(
                request.accountNumber(),
                lookupResult.accountName(),
                bank.getCode(),
                bank.getName()
        );
    }

    @Override
    @Transactional
    public MerchantPayoutAccountResponse createPayoutAccount(
            CreatePayoutAccountRequest request
    ) {
        Tenant tenant = authenticatedTenantProvider.getCurrentTenant();

        validateTenantIsActive(tenant);
        validateAccountNumber(request.accountNumber());

        NombaBank bank = findActiveBankByCode(request.bankCode());

        NombaBankAccountLookupResult lookupResult =
                nombaBankGateway.lookupBankAccount(
                        request.accountNumber(),
                        request.bankCode()
                );

        if (!lookupResult.success() || !hasText(lookupResult.accountName())) {
            throw new BadRequestException("Unable to verify bank account");
        }

        if (hasText(request.accountName())
                && !sameName(request.accountName(), lookupResult.accountName())) {
            throw new BadRequestException(
                    "Provided account name does not match verified account name"
            );
        }

        ensureDuplicateBankAccountDoesNotExist(
                tenant.getId(),
                request.bankCode(),
                request.accountNumber()
        );

        if (request.defaultAccount()) {
            unsetExistingDefaultAccounts(tenant.getId());
        }

        MerchantPayoutAccount payoutAccount =
                MerchantPayoutAccount.builder()
                        .tenant(tenant)
                        .destinationType(PayoutDestinationType.BANK_ACCOUNT)
                        .bankCode(bank.getCode())
                        .bankName(bank.getName())
                        .accountNumber(request.accountNumber())
                        .accountName(lookupResult.accountName())
                        .status(PayoutAccountStatus.VERIFIED)
                        .defaultAccount(request.defaultAccount())
                        .verifiedAt(Instant.now())
                        .build();

        MerchantPayoutAccount savedAccount =
                payoutAccountRepository.save(payoutAccount);

        log.info(
                "Merchant payout account created. tenantId={}, payoutAccountId={}, bankCode={}, accountNumberMasked={}",
                tenant.getId(),
                savedAccount.getId(),
                savedAccount.getBankCode(),
                maskAccountNumber(savedAccount.getAccountNumber())
        );

        return MerchantPayoutAccountResponse.from(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchantPayoutAccountResponse> listPayoutAccounts() {
        Tenant tenant = authenticatedTenantProvider.getCurrentTenant();

        return payoutAccountRepository
                .findAllByTenant_IdAndStatusOrderByCreatedAtDesc(
                        tenant.getId(),
                        PayoutAccountStatus.VERIFIED
                )
                .stream()
                .map(MerchantPayoutAccountResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MerchantPayoutAccountResponse disablePayoutAccount(
            UUID payoutAccountId
    ) {
        Tenant tenant = authenticatedTenantProvider.getCurrentTenant();

        MerchantPayoutAccount payoutAccount =
                payoutAccountRepository.findByIdAndTenant_Id(
                                payoutAccountId,
                                tenant.getId()
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payout account not found"
                        ));

        if (payoutAccount.getStatus() == PayoutAccountStatus.DISABLED) {
            return MerchantPayoutAccountResponse.from(payoutAccount);
        }

        payoutAccount.setStatus(PayoutAccountStatus.DISABLED);
        payoutAccount.setDefaultAccount(false);
        payoutAccount.setDisabledAt(Instant.now());

        log.info(
                "Merchant payout account disabled. tenantId={}, payoutAccountId={}",
                tenant.getId(),
                payoutAccount.getId()
        );

        return MerchantPayoutAccountResponse.from(payoutAccount);
    }

    private NombaBank findActiveBankByCode(String bankCode) {
        if (!hasText(bankCode)) {
            throw new BadRequestException("Bank code is required");
        }

        NombaBank bank = nombaBankRepository.findByCode(bankCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank not found. Please sync banks from Nomba first."
                ));

        if (bank.getStatus() != BankStatus.ACTIVE) {
            throw new BadRequestException("Bank is not active");
        }

        return bank;
    }

    private void ensureDuplicateBankAccountDoesNotExist(
            UUID tenantId,
            String bankCode,
            String accountNumber
    ) {
        boolean duplicateExists =
                payoutAccountRepository
                        .findAllByTenant_IdAndStatusOrderByCreatedAtDesc(
                                tenantId,
                                PayoutAccountStatus.VERIFIED
                        )
                        .stream()
                        .anyMatch(account ->
                                account.getDestinationType()
                                        == PayoutDestinationType.BANK_ACCOUNT
                                        && bankCode.equals(account.getBankCode())
                                        && accountNumber.equals(account.getAccountNumber())
                        );

        if (duplicateExists) {
            throw new BadRequestException(
                    "This payout account already exists"
            );
        }
    }

    private void unsetExistingDefaultAccounts(UUID tenantId) {
        List<MerchantPayoutAccount> accounts =
                payoutAccountRepository
                        .findAllByTenant_IdAndStatusOrderByCreatedAtDesc(
                                tenantId,
                                PayoutAccountStatus.VERIFIED
                        );

        for (MerchantPayoutAccount account : accounts) {
            account.setDefaultAccount(false);
        }
    }

    private void validateTenantIsActive(Tenant tenant) {
        if (tenant == null) {
            throw new BadRequestException("Tenant is required");
        }

        if (tenant.getStatus() != null
                && !"ACTIVE".equalsIgnoreCase(tenant.getStatus().name())) {
            throw new BadRequestException("Tenant is not active");
        }
    }

    private void validateAccountNumber(String accountNumber) {
        if (!hasText(accountNumber)) {
            throw new BadRequestException("Account number is required");
        }

        if (!accountNumber.matches("\\d{10}")) {
            throw new BadRequestException("Account number must be 10 digits");
        }
    }

    private boolean sameName(String left, String right) {
        return normalizeName(left).equals(normalizeName(right));
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }

        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}