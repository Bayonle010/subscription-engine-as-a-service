package com.markbay.subscription_engine.merchantwithdrawal.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ConflictException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.ledger.service.LedgerPostingService;
import com.markbay.subscription_engine.merchantwithdrawal.dto.CreateMerchantWithdrawalRequest;
import com.markbay.subscription_engine.merchantwithdrawal.dto.MerchantWithdrawalResponse;
import com.markbay.subscription_engine.merchantwithdrawal.entity.MerchantWithdrawal;
import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalProvider;
import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalStatus;
import com.markbay.subscription_engine.merchantwithdrawal.repository.MerchantWithdrawalRepository;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalService;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalVerificationService;
import com.markbay.subscription_engine.nomba.dto.request.NombaBankTransferRequest;
import com.markbay.subscription_engine.nomba.dto.request.NombaWalletTransferRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaTransferResult;
import com.markbay.subscription_engine.nomba.gateway.NombaTransferGateway;
import com.markbay.subscription_engine.payoutaccount.entity.MerchantPayoutAccount;
import com.markbay.subscription_engine.payoutaccount.enums.PayoutAccountStatus;
import com.markbay.subscription_engine.payoutaccount.enums.PayoutDestinationType;
import com.markbay.subscription_engine.payoutaccount.repository.MerchantPayoutAccountRepository;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantWithdrawalServiceImpl implements MerchantWithdrawalService {

    private static final String SUPPORTED_CURRENCY = "NGN";

    private static final Set<String> TERMINAL_FAILED_STATUSES = Set.of(
            "FAILED",
            "FAILURE",
            "DECLINED",
            "CANCELLED",
            "CANCELED",
            "REJECTED",
            "REFUND",
            "REFUNDED",
            "REVERSED"
    );

    private final AuthenticatedTenantProvider authenticatedTenantProvider;
    private final TenantRepository tenantRepository;
    private final MerchantPayoutAccountRepository payoutAccountRepository;
    private final MerchantWithdrawalRepository withdrawalRepository;
    private final LedgerPostingService ledgerPostingService;
    private final NombaTransferGateway nombaTransferGateway;
    private final PlatformTransactionManager transactionManager;
    private final MerchantWithdrawalVerificationService withdrawalVerificationService;

    @Override
    public MerchantWithdrawalResponse requestWithdrawal(
            String idempotencyKey,
            CreateMerchantWithdrawalRequest request
    ) {
        Tenant tenant = getCurrentTenant();

        validateTenantIsActive(tenant);
        validateIdempotencyKey(idempotencyKey);

        BigDecimal amount = normalizeAmount(request.amount());
        String currency = normalizeCurrency(request.currency());

        MerchantPayoutAccount payoutAccount =
                payoutAccountRepository.findByIdAndTenant_Id(
                                request.payoutAccountId(),
                                tenant.getId()
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payout account not found"
                        ));

        validatePayoutAccount(payoutAccount);

        String requestHash = hashRequest(
                payoutAccount.getDestinationType().name(),
                payoutAccount.getId().toString(),
                amount.toPlainString(),
                currency,
                safe(request.narration())
        );

        Optional<MerchantWithdrawal> existingWithdrawal =
                withdrawalRepository.findByTenant_IdAndIdempotencyKey(
                        tenant.getId(),
                        idempotencyKey
                );

        if (existingWithdrawal.isPresent()) {
            MerchantWithdrawal existing = existingWithdrawal.get();

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ConflictException(
                        "Idempotency key has already been used for a different withdrawal request"
                );
            }

            if (existing.getStatus() == MerchantWithdrawalStatus.HELD) {
                return dispatchWithdrawalAndReturn(
                        existing.getId(),
                        tenant.getId()
                );
            }

            return MerchantWithdrawalResponse.from(existing);
        }

        MerchantWithdrawal withdrawal =
                buildWithdrawal(
                        tenant,
                        payoutAccount,
                        amount,
                        currency,
                        request.narration(),
                        idempotencyKey,
                        requestHash
                );

        MerchantWithdrawal savedWithdrawal =
                createWithdrawalAndHoldFunds(withdrawal);

        log.info(
                "Merchant withdrawal requested and funds held. tenantId={}, withdrawalId={}, destinationType={}, amount={}, currency={}, merchantTxRef={}",
                tenant.getId(),
                savedWithdrawal.getId(),
                savedWithdrawal.getDestinationType(),
                savedWithdrawal.getAmount(),
                savedWithdrawal.getCurrency(),
                savedWithdrawal.getMerchantTxRef()
        );

        return dispatchWithdrawalAndReturn(
                savedWithdrawal.getId(),
                tenant.getId()
        );
    }

    @Override
    public List<MerchantWithdrawalResponse> listWithdrawals() {
        Tenant tenant = getCurrentTenant();

        return withdrawalRepository
                .findAllByTenant_IdOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .map(MerchantWithdrawalResponse::from)
                .toList();
    }

    @Override
    public MerchantWithdrawalResponse getWithdrawal(UUID withdrawalId) {
        Tenant tenant = getCurrentTenant();

        MerchantWithdrawal withdrawal =
                withdrawalRepository.findByIdAndTenant_Id(
                                withdrawalId,
                                tenant.getId()
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Merchant withdrawal not found"
                        ));

        return MerchantWithdrawalResponse.from(withdrawal);
    }

    @Override
    public MerchantWithdrawalResponse retryWithdrawal(UUID withdrawalId) {
        Tenant tenant = getCurrentTenant();

        MerchantWithdrawal withdrawal =
                withdrawalRepository.findByIdAndTenant_Id(
                                withdrawalId,
                                tenant.getId()
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Merchant withdrawal not found"
                        ));

        if (withdrawal.getStatus() == MerchantWithdrawalStatus.SUCCEEDED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.REVERSED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.FAILED) {
            return MerchantWithdrawalResponse.from(withdrawal);
        }

        return dispatchWithdrawalAndReturn(
                withdrawal.getId(),
                tenant.getId()
        );
    }

    private MerchantWithdrawal buildWithdrawal(
            Tenant tenant,
            MerchantPayoutAccount payoutAccount,
            BigDecimal amount,
            String currency,
            String narration,
            String idempotencyKey,
            String requestHash
    ) {
        MerchantWithdrawal.MerchantWithdrawalBuilder builder =
                MerchantWithdrawal.builder()
                        .tenant(tenant)
                        .payoutAccount(payoutAccount)
                        .destinationType(payoutAccount.getDestinationType())
                        .status(MerchantWithdrawalStatus.HELD)
                        .provider(MerchantWithdrawalProvider.NOMBA)
                        .amount(amount)
                        .currency(currency)
                        .narration(resolveNarration(narration))
                        .idempotencyKey(idempotencyKey)
                        .requestHash(requestHash)
                        .merchantTxRef(buildMerchantTxRef())
                        .heldAt(Instant.now())
                        .nextAttemptAt(null);

        if (payoutAccount.getDestinationType() == PayoutDestinationType.BANK_ACCOUNT) {
            builder.bankCode(payoutAccount.getBankCode())
                    .bankName(payoutAccount.getBankName())
                    .accountNumber(payoutAccount.getAccountNumber())
                    .accountName(payoutAccount.getAccountName());
        }

        if (payoutAccount.getDestinationType() == PayoutDestinationType.NOMBA_WALLET) {
            builder.receiverAccountId(payoutAccount.getReceiverAccountId());
        }

        return builder.build();
    }

    private MerchantWithdrawal createWithdrawalAndHoldFunds(
            MerchantWithdrawal withdrawal
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        return transactionTemplate.execute(status -> {
            MerchantWithdrawal savedWithdrawal =
                    withdrawalRepository.save(withdrawal);

            LedgerPostingResult holdResult =
                    ledgerPostingService.holdMerchantWithdrawal(
                            savedWithdrawal.getTenant(),
                            savedWithdrawal.getId(),
                            savedWithdrawal.getAmount(),
                            savedWithdrawal.getCurrency()
                    );

            savedWithdrawal.setHoldLedgerTransactionRef(
                    holdResult.transactionRef()
            );

            return savedWithdrawal;
        });
    }

    private MerchantWithdrawalResponse dispatchWithdrawalAndReturn(
            UUID withdrawalId,
            UUID tenantId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        MerchantWithdrawal withdrawal =
                transactionTemplate.execute(status ->
                        markWithdrawalAsDispatching(
                                withdrawalId,
                                tenantId
                        )
                );

        if (withdrawal == null) {
            return getWithdrawalForTenant(
                    withdrawalId,
                    tenantId
            );
        }

        try {
            NombaTransferResult transferResult =
                    callNombaTransfer(withdrawal);

            withdrawalVerificationService.applyInitialTransferResult(
                    withdrawalId,
                    transferResult
            );
        } catch (Exception exception) {
            log.error(
                    "Nomba withdrawal dispatch call failed. withdrawalId={}, reason={}",
                    withdrawalId,
                    exception.getMessage(),
                    exception
            );

            transactionTemplate.executeWithoutResult(status ->
                    recordDispatchException(
                            withdrawalId,
                            tenantId,
                            exception
                    )
            );
        }

        return getWithdrawalForTenant(
                withdrawalId,
                tenantId
        );
    }

    private MerchantWithdrawal markWithdrawalAsDispatching(
            UUID withdrawalId,
            UUID tenantId
    ) {
        MerchantWithdrawal withdrawal =
                withdrawalRepository.findByIdForUpdate(withdrawalId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Merchant withdrawal not found"
                        ));

        if (!withdrawal.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Merchant withdrawal not found");
        }

        if (withdrawal.getStatus() == MerchantWithdrawalStatus.SUCCEEDED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.REVERSED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.FAILED) {
            log.info(
                    "Skipping withdrawal dispatch because withdrawal is already terminal. withdrawalId={}, status={}",
                    withdrawal.getId(),
                    withdrawal.getStatus()
            );
            return null;
        }

        if (withdrawal.getStatus() == MerchantWithdrawalStatus.DISPATCHING) {
            log.info(
                    "Skipping withdrawal dispatch because withdrawal is already dispatching. withdrawalId={}",
                    withdrawal.getId()
            );
            return null;
        }

        if (withdrawal.getAttemptCount() >= withdrawal.getMaxAttempts()) {
            withdrawal.setStatus(MerchantWithdrawalStatus.MANUAL_REVIEW);
            withdrawal.setFailureReason("Maximum dispatch attempts reached");
            withdrawal.setNextAttemptAt(null);
            return null;
        }

        withdrawal.setStatus(MerchantWithdrawalStatus.DISPATCHING);
        withdrawal.setAttemptCount(withdrawal.getAttemptCount() + 1);
        withdrawal.setDispatchedAt(Instant.now());
        withdrawal.setFailureReason(null);

        return withdrawal;
    }

    private NombaTransferResult callNombaTransfer(
            MerchantWithdrawal withdrawal
    ) {
        if (withdrawal.getDestinationType() == PayoutDestinationType.BANK_ACCOUNT) {
            return nombaTransferGateway.transferToBank(
                    new NombaBankTransferRequest(
                            withdrawal.getAmount().setScale(2, RoundingMode.HALF_UP),
                            withdrawal.getAccountNumber(),
                            withdrawal.getAccountName(),
                            withdrawal.getBankCode(),
                            withdrawal.getMerchantTxRef(),
                            buildSenderName(withdrawal),
                            withdrawal.getNarration()
                    )
            );
        }

        if (withdrawal.getDestinationType() == PayoutDestinationType.NOMBA_WALLET) {
            return nombaTransferGateway.transferToWallet(
                    new NombaWalletTransferRequest(
                            withdrawal.getAmount().setScale(2, RoundingMode.HALF_UP),
                            withdrawal.getReceiverAccountId(),
                            withdrawal.getMerchantTxRef(),
                            withdrawal.getNarration()
                    )
            );
        }

        throw new BadRequestException("Unsupported withdrawal destination type");
    }


    private void recordDispatchException(
            UUID withdrawalId,
            UUID tenantId,
            Exception exception
    ) {
        MerchantWithdrawal withdrawal =
                withdrawalRepository.findByIdForUpdate(withdrawalId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Merchant withdrawal not found"
                        ));

        if (!withdrawal.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Merchant withdrawal not found");
        }

        if (withdrawal.getStatus() == MerchantWithdrawalStatus.SUCCEEDED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.REVERSED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.FAILED) {
            return;
        }

        withdrawal.setStatus(MerchantWithdrawalStatus.PROCESSING);
        withdrawal.setFailureReason(
                "Nomba transfer call failed or returned unknown result: "
                        + exception.getMessage()
        );
        withdrawal.setNextAttemptAt(null);

        log.warn(
                "Merchant withdrawal left in PROCESSING after dispatch exception. withdrawalId={}, merchantTxRef={}",
                withdrawal.getId(),
                withdrawal.getMerchantTxRef()
        );
    }


    private MerchantWithdrawalResponse getWithdrawalForTenant(
            UUID withdrawalId,
            UUID tenantId
    ) {
        MerchantWithdrawal withdrawal =
                withdrawalRepository.findByIdAndTenant_Id(
                                withdrawalId,
                                tenantId
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Merchant withdrawal not found"
                        ));

        return MerchantWithdrawalResponse.from(withdrawal);
    }

    private void validatePayoutAccount(
            MerchantPayoutAccount payoutAccount
    ) {
        if (payoutAccount.getStatus() != PayoutAccountStatus.VERIFIED) {
            throw new BadRequestException("Payout account is not verified");
        }

        if (payoutAccount.getDestinationType() == PayoutDestinationType.BANK_ACCOUNT) {
            validateBankPayoutAccount(payoutAccount);
            return;
        }

        if (payoutAccount.getDestinationType() == PayoutDestinationType.NOMBA_WALLET) {
            validateNombaWalletPayoutAccount(payoutAccount);
            return;
        }

        throw new BadRequestException("Unsupported payout account destination type");
    }

    private void validateBankPayoutAccount(
            MerchantPayoutAccount payoutAccount
    ) {
        if (!hasText(payoutAccount.getBankCode())
                || !hasText(payoutAccount.getAccountNumber())
                || !hasText(payoutAccount.getAccountName())) {
            throw new BadRequestException("Bank payout account is incomplete");
        }
    }

    private void validateNombaWalletPayoutAccount(
            MerchantPayoutAccount payoutAccount
    ) {
        if (!hasText(payoutAccount.getReceiverAccountId())) {
            throw new BadRequestException("Nomba wallet payout account is incomplete");
        }
    }

    private Tenant getCurrentTenant() {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
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

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        if (idempotencyKey.length() < 8 || idempotencyKey.length() > 120) {
            throw new BadRequestException(
                    "Idempotency-Key must be between 8 and 120 characters"
            );
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("Amount is required");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }

        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        if (!hasText(currency)) {
            throw new BadRequestException("Currency is required");
        }

        String normalizedCurrency = currency.trim().toUpperCase();

        if (!SUPPORTED_CURRENCY.equals(normalizedCurrency)) {
            throw new BadRequestException(
                    "Only NGN withdrawals are currently supported"
            );
        }

        return normalizedCurrency;
    }

    private String buildMerchantTxRef() {
        return "mw_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildSenderName(MerchantWithdrawal withdrawal) {
        if (withdrawal.getTenant() != null
                && hasText(withdrawal.getTenant().getBusinessName())) {
            return withdrawal.getTenant().getBusinessName();
        }

        return "Merchant";
    }

    private String resolveNarration(String narration) {
        if (hasText(narration)) {
            return narration.trim();
        }

        return "Merchant withdrawal";
    }

    private boolean isTerminalFailureStatus(String providerStatus) {
        if (!hasText(providerStatus)) {
            return false;
        }

        return TERMINAL_FAILED_STATUSES.contains(
                providerStatus.trim().toUpperCase()
        );
    }

    private String hashRequest(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            String joined = String.join("|", parts);

            byte[] hash = digest.digest(
                    joined.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash withdrawal request");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}