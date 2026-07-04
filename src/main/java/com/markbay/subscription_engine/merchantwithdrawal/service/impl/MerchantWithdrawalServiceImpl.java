package com.markbay.subscription_engine.merchantwithdrawal.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ConflictException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.ledger.service.LedgerPostingService;
import com.markbay.subscription_engine.merchantwithdrawal.dto.CreateBankMerchantWithdrawalRequest;
import com.markbay.subscription_engine.merchantwithdrawal.dto.CreateNombaWalletMerchantWithdrawalRequest;
import com.markbay.subscription_engine.merchantwithdrawal.dto.MerchantWithdrawalResponse;
import com.markbay.subscription_engine.merchantwithdrawal.entity.MerchantWithdrawal;
import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalProvider;
import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalStatus;
import com.markbay.subscription_engine.merchantwithdrawal.repository.MerchantWithdrawalRepository;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalService;
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
    private final MerchantPayoutAccountRepository payoutAccountRepository;
    private final MerchantWithdrawalRepository withdrawalRepository;
    private final LedgerPostingService ledgerPostingService;
    private final NombaTransferGateway nombaTransferGateway;
    private final PlatformTransactionManager transactionManager;

    @Override
    public MerchantWithdrawalResponse requestBankWithdrawal(
            String idempotencyKey,
            CreateBankMerchantWithdrawalRequest request
    ) {
        Tenant tenant = authenticatedTenantProvider.getCurrentTenant();

        validateTenantIsActive(tenant);
        validateIdempotencyKey(idempotencyKey);

        BigDecimal amount = normalizeAmount(request.amount());
        String currency = normalizeCurrency(request.currency());

        String requestHash = hashRequest(
                "BANK_ACCOUNT",
                request.payoutAccountId().toString(),
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
            return handleExistingIdempotentWithdrawal(
                    existingWithdrawal.get(),
                    requestHash
            );
        }

        MerchantPayoutAccount payoutAccount =
                payoutAccountRepository.findByIdAndTenant_Id(
                                request.payoutAccountId(),
                                tenant.getId()
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payout account not found"
                        ));

        validateBankPayoutAccount(payoutAccount);

        MerchantWithdrawal withdrawal =
                MerchantWithdrawal.builder()
                        .tenant(tenant)
                        .payoutAccount(payoutAccount)
                        .destinationType(PayoutDestinationType.BANK_ACCOUNT)
                        .status(MerchantWithdrawalStatus.HELD)
                        .provider(MerchantWithdrawalProvider.NOMBA)
                        .amount(amount)
                        .currency(currency)
                        .bankCode(payoutAccount.getBankCode())
                        .bankName(payoutAccount.getBankName())
                        .accountNumber(payoutAccount.getAccountNumber())
                        .accountName(payoutAccount.getAccountName())
                        .narration(resolveNarration(request.narration()))
                        .idempotencyKey(idempotencyKey)
                        .requestHash(requestHash)
                        .merchantTxRef(buildMerchantTxRef())
                        .heldAt(Instant.now())
                        .nextAttemptAt(Instant.now())
                        .build();

        MerchantWithdrawal savedWithdrawal =
                createWithdrawalAndHoldFunds(withdrawal);

        log.info(
                "Merchant bank withdrawal requested and funds held. tenantId={}, withdrawalId={}, amount={}, currency={}, merchantTxRef={}",
                tenant.getId(),
                savedWithdrawal.getId(),
                savedWithdrawal.getAmount(),
                savedWithdrawal.getCurrency(),
                savedWithdrawal.getMerchantTxRef()
        );

        return MerchantWithdrawalResponse.from(savedWithdrawal);
    }

    @Override
    public MerchantWithdrawalResponse requestNombaWalletWithdrawal(
            String idempotencyKey,
            CreateNombaWalletMerchantWithdrawalRequest request
    ) {
        Tenant tenant = authenticatedTenantProvider.getCurrentTenant();

        validateTenantIsActive(tenant);
        validateIdempotencyKey(idempotencyKey);
        validateReceiverAccountId(request.receiverAccountId());

        BigDecimal amount = normalizeAmount(request.amount());
        String currency = normalizeCurrency(request.currency());

        String requestHash = hashRequest(
                "NOMBA_WALLET",
                request.receiverAccountId(),
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
            return handleExistingIdempotentWithdrawal(
                    existingWithdrawal.get(),
                    requestHash
            );
        }

        MerchantWithdrawal withdrawal =
                MerchantWithdrawal.builder()
                        .tenant(tenant)
                        .destinationType(PayoutDestinationType.NOMBA_WALLET)
                        .status(MerchantWithdrawalStatus.HELD)
                        .provider(MerchantWithdrawalProvider.NOMBA)
                        .amount(amount)
                        .currency(currency)
                        .receiverAccountId(request.receiverAccountId().trim())
                        .narration(resolveNarration(request.narration()))
                        .idempotencyKey(idempotencyKey)
                        .requestHash(requestHash)
                        .merchantTxRef(buildMerchantTxRef())
                        .heldAt(Instant.now())
                        .nextAttemptAt(Instant.now())
                        .build();

        MerchantWithdrawal savedWithdrawal =
                createWithdrawalAndHoldFunds(withdrawal);

        log.info(
                "Merchant Nomba wallet withdrawal requested and funds held. tenantId={}, withdrawalId={}, amount={}, currency={}, merchantTxRef={}",
                tenant.getId(),
                savedWithdrawal.getId(),
                savedWithdrawal.getAmount(),
                savedWithdrawal.getCurrency(),
                savedWithdrawal.getMerchantTxRef()
        );

        return MerchantWithdrawalResponse.from(savedWithdrawal);
    }

    @Override
    public List<MerchantWithdrawalResponse> listWithdrawals() {
        Tenant tenant = authenticatedTenantProvider.getCurrentTenant();

        return withdrawalRepository
                .findAllByTenant_IdOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .map(MerchantWithdrawalResponse::from)
                .toList();
    }

    @Override
    public MerchantWithdrawalResponse getWithdrawal(UUID withdrawalId) {
        Tenant tenant = authenticatedTenantProvider.getCurrentTenant();

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
    public void dispatchWithdrawal(UUID withdrawalId) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        MerchantWithdrawal withdrawal =
                transactionTemplate.execute(status ->
                        markWithdrawalAsDispatching(withdrawalId)
                );

        if (withdrawal == null) {
            return;
        }

        try {
            NombaTransferResult transferResult =
                    callNombaTransfer(withdrawal);

            transactionTemplate.executeWithoutResult(status ->
                    recordDispatchResult(
                            withdrawalId,
                            transferResult
                    )
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
                            exception
                    )
            );
        }
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

    private MerchantWithdrawalResponse handleExistingIdempotentWithdrawal(
            MerchantWithdrawal existingWithdrawal,
            String requestHash
    ) {
        if (!existingWithdrawal.getRequestHash().equals(requestHash)) {
            throw new ConflictException(
                    "Idempotency key has already been used for a different withdrawal request"
            );
        }

        return MerchantWithdrawalResponse.from(existingWithdrawal);
    }

    private MerchantWithdrawal markWithdrawalAsDispatching(UUID withdrawalId) {
        MerchantWithdrawal withdrawal =
                withdrawalRepository.findByIdForUpdate(withdrawalId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Merchant withdrawal not found"
                        ));

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

        if (withdrawal.getStatus() != MerchantWithdrawalStatus.HELD
                && withdrawal.getStatus() != MerchantWithdrawalStatus.PROCESSING) {
            log.info(
                    "Skipping withdrawal dispatch because withdrawal is not dispatchable. withdrawalId={}, status={}",
                    withdrawal.getId(),
                    withdrawal.getStatus()
            );
            return null;
        }

        if (withdrawal.getAttemptCount() >= withdrawal.getMaxAttempts()) {
            withdrawal.setStatus(MerchantWithdrawalStatus.MANUAL_REVIEW);
            withdrawal.setFailureReason("Maximum dispatch attempts reached");
            withdrawal.setNextAttemptAt(null);

            log.warn(
                    "Withdrawal moved to manual review after max attempts. withdrawalId={}, attempts={}",
                    withdrawal.getId(),
                    withdrawal.getAttemptCount()
            );

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

    private void recordDispatchResult(
            UUID withdrawalId,
            NombaTransferResult result
    ) {
        MerchantWithdrawal withdrawal =
                withdrawalRepository.findByIdForUpdate(withdrawalId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Merchant withdrawal not found"
                        ));

        if (withdrawal.getStatus() == MerchantWithdrawalStatus.SUCCEEDED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.REVERSED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.FAILED) {
            return;
        }

        if (result == null) {
            withdrawal.setStatus(MerchantWithdrawalStatus.PROCESSING);
            withdrawal.setFailureReason("Nomba transfer response was empty");
            withdrawal.setNextAttemptAt(nextAttemptAt(withdrawal));
            return;
        }

        if (hasText(result.transferId())) {
            withdrawal.setProviderTransferId(result.transferId());
        }

        withdrawal.setProviderStatus(result.status());
        withdrawal.setProviderRawResponse(result.rawResponse());

        if (result.successful()) {
            settleSuccessfulWithdrawal(withdrawal);
            return;
        }

        if (result.pending() || result.accepted()) {
            withdrawal.setStatus(MerchantWithdrawalStatus.PROCESSING);
            withdrawal.setFailureReason(null);
            withdrawal.setNextAttemptAt(nextAttemptAt(withdrawal));

            log.info(
                    "Merchant withdrawal is processing at Nomba. withdrawalId={}, merchantTxRef={}, providerStatus={}",
                    withdrawal.getId(),
                    withdrawal.getMerchantTxRef(),
                    result.status()
            );

            return;
        }

        if (isTerminalFailureStatus(result.status())) {
            releaseFailedWithdrawalHold(
                    withdrawal,
                    "Nomba transfer failed with status: " + result.status()
            );
            return;
        }

        withdrawal.setStatus(MerchantWithdrawalStatus.MANUAL_REVIEW);
        withdrawal.setFailureReason("Unexpected Nomba transfer response");
        withdrawal.setNextAttemptAt(null);

        log.warn(
                "Merchant withdrawal moved to manual review due to unexpected provider response. withdrawalId={}, merchantTxRef={}, providerStatus={}",
                withdrawal.getId(),
                withdrawal.getMerchantTxRef(),
                result.status()
        );
    }

    private void recordDispatchException(
            UUID withdrawalId,
            Exception exception
    ) {
        MerchantWithdrawal withdrawal =
                withdrawalRepository.findByIdForUpdate(withdrawalId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Merchant withdrawal not found"
                        ));

        if (withdrawal.getStatus() == MerchantWithdrawalStatus.SUCCEEDED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.REVERSED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.FAILED) {
            return;
        }

        if (withdrawal.getAttemptCount() >= withdrawal.getMaxAttempts()) {
            withdrawal.setStatus(MerchantWithdrawalStatus.MANUAL_REVIEW);
            withdrawal.setFailureReason(
                    "Maximum dispatch attempts reached. Last error: "
                            + exception.getMessage()
            );
            withdrawal.setNextAttemptAt(null);
            return;
        }

        withdrawal.setStatus(MerchantWithdrawalStatus.PROCESSING);
        withdrawal.setFailureReason(exception.getMessage());
        withdrawal.setNextAttemptAt(nextAttemptAt(withdrawal));
    }

    private void settleSuccessfulWithdrawal(
            MerchantWithdrawal withdrawal
    ) {
        LedgerPostingResult settlementResult =
                ledgerPostingService.settleMerchantWithdrawal(
                        withdrawal.getTenant(),
                        withdrawal.getId(),
                        withdrawal.getAmount(),
                        withdrawal.getCurrency()
                );

        withdrawal.setSettlementLedgerTransactionRef(
                settlementResult.transactionRef()
        );

        withdrawal.setStatus(MerchantWithdrawalStatus.SUCCEEDED);
        withdrawal.setSucceededAt(Instant.now());
        withdrawal.setNextAttemptAt(null);
        withdrawal.setFailureReason(null);

        log.info(
                "Merchant withdrawal succeeded. tenantId={}, withdrawalId={}, merchantTxRef={}, amount={}, currency={}",
                withdrawal.getTenant().getId(),
                withdrawal.getId(),
                withdrawal.getMerchantTxRef(),
                withdrawal.getAmount(),
                withdrawal.getCurrency()
        );
    }

    private void releaseFailedWithdrawalHold(
            MerchantWithdrawal withdrawal,
            String reason
    ) {
        LedgerPostingResult reversalResult =
                ledgerPostingService.releaseMerchantWithdrawalHold(
                        withdrawal.getTenant(),
                        withdrawal.getId(),
                        withdrawal.getAmount(),
                        withdrawal.getCurrency()
                );

        withdrawal.setReversalLedgerTransactionRef(
                reversalResult.transactionRef()
        );

        withdrawal.setStatus(MerchantWithdrawalStatus.REVERSED);
        withdrawal.setFailedAt(Instant.now());
        withdrawal.setReversedAt(Instant.now());
        withdrawal.setNextAttemptAt(null);
        withdrawal.setFailureReason(reason);

        log.warn(
                "Merchant withdrawal failed and hold was released. tenantId={}, withdrawalId={}, merchantTxRef={}, reason={}",
                withdrawal.getTenant().getId(),
                withdrawal.getId(),
                withdrawal.getMerchantTxRef(),
                reason
        );
    }

    private void validateBankPayoutAccount(
            MerchantPayoutAccount payoutAccount
    ) {
        if (payoutAccount.getStatus() != PayoutAccountStatus.VERIFIED) {
            throw new BadRequestException("Payout account is not verified");
        }

        if (payoutAccount.getDestinationType() != PayoutDestinationType.BANK_ACCOUNT) {
            throw new BadRequestException("Payout account is not a bank account");
        }

        if (!hasText(payoutAccount.getBankCode())
                || !hasText(payoutAccount.getAccountNumber())
                || !hasText(payoutAccount.getAccountName())) {
            throw new BadRequestException("Payout account is incomplete");
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

    private void validateReceiverAccountId(String receiverAccountId) {
        if (!hasText(receiverAccountId)) {
            throw new BadRequestException("Receiver account ID is required");
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

    private Instant nextAttemptAt(MerchantWithdrawal withdrawal) {
        int attempt = Math.max(withdrawal.getAttemptCount(), 1);

        long delaySeconds = Math.min(
                1_800L,
                120L * attempt
        );

        return Instant.now().plusSeconds(delaySeconds);
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