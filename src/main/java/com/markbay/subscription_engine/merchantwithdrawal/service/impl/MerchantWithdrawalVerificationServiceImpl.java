package com.markbay.subscription_engine.merchantwithdrawal.service.impl;

import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.ledger.service.LedgerPostingService;
import com.markbay.subscription_engine.merchantwithdrawal.dto.NombaPayoutWebhookData;
import com.markbay.subscription_engine.merchantwithdrawal.entity.MerchantWithdrawal;
import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalStatus;
import com.markbay.subscription_engine.merchantwithdrawal.repository.MerchantWithdrawalRepository;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalVerificationService;
import com.markbay.subscription_engine.nomba.dto.response.NombaTransferResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaTransferStatusResult;
import com.markbay.subscription_engine.nomba.gateway.NombaTransferStatusGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantWithdrawalVerificationServiceImpl
        implements MerchantWithdrawalVerificationService {

    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "SUCCESS",
            "SUCCESSFUL",
            "COMPLETED"
    );

    private static final Set<String> PENDING_STATUSES = Set.of(
            "NEW",
            "PENDING",
            "PROCESSING",
            "PENDING_BILLING",
            "UNKNOWN"
    );

    private static final Set<String> REVERSED_STATUSES = Set.of(
            "REFUND",
            "REFUNDED",
            "REVERSED"
    );

    private static final Set<String> FAILED_STATUSES = Set.of(
            "FAILED",
            "FAILURE",
            "DECLINED",
            "CANCELLED",
            "CANCELED",
            "REJECTED"
    );

    private final MerchantWithdrawalRepository withdrawalRepository;
    private final LedgerPostingService ledgerPostingService;
    private final NombaTransferStatusGateway transferStatusGateway;

    @Override
    @Transactional
    public void applyInitialTransferResult(
            UUID withdrawalId,
            NombaTransferResult transferResult
    ) {
        MerchantWithdrawal withdrawal = lockWithdrawal(withdrawalId);

        if (isTerminal(withdrawal)) {
            return;
        }

        if (transferResult == null) {
            markProcessing(
                    withdrawal,
                    "Nomba transfer response was empty"
            );
            return;
        }

        if (hasText(transferResult.transferId())) {
            withdrawal.setProviderTransferId(transferResult.transferId());
        }

        withdrawal.setProviderStatus(transferResult.status());
        withdrawal.setProviderRawResponse(transferResult.rawResponse());

        if (transferResult.successful()) {
            settleSuccessfulWithdrawal(
                    withdrawal,
                    "Initial Nomba transfer response"
            );
            return;
        }

        if (transferResult.pending() || transferResult.accepted()) {
            markProcessing(
                    withdrawal,
                    null
            );
            return;
        }

        String normalizedStatus = normalize(transferResult.status());

        if (REVERSED_STATUSES.contains(normalizedStatus)
                || FAILED_STATUSES.contains(normalizedStatus)) {
            releaseWithdrawalHold(
                    withdrawal,
                    "Nomba transfer failed with status: " + transferResult.status()
            );
            return;
        }

        markProcessing(
                withdrawal,
                "Unexpected initial Nomba transfer response"
        );
    }

    @Override
    @Transactional
    public void handlePayoutWebhook(
            NombaPayoutWebhookData payoutWebhookData
    ) {
        if (payoutWebhookData == null) {
            log.warn("Ignoring payout webhook because payload data is null");
            return;
        }

        MerchantWithdrawal withdrawal = findWithdrawalForWebhook(
                payoutWebhookData
        );

        if (withdrawal == null) {
            log.warn(
                    "Payout webhook ignored because merchant withdrawal was not found. merchantTxRef={}, transferId={}, eventType={}",
                    payoutWebhookData.merchantTxRef(),
                    payoutWebhookData.transferId(),
                    payoutWebhookData.eventType()
            );
            return;
        }

        MerchantWithdrawal lockedWithdrawal =
                lockWithdrawal(withdrawal.getId());

        if (isTerminal(lockedWithdrawal)) {
            log.info(
                    "Payout webhook ignored because withdrawal is already terminal. withdrawalId={}, status={}",
                    lockedWithdrawal.getId(),
                    lockedWithdrawal.getStatus()
            );
            return;
        }

        if (hasText(payoutWebhookData.transferId())) {
            lockedWithdrawal.setProviderTransferId(
                    payoutWebhookData.transferId()
            );
        }

        lockedWithdrawal.setProviderStatus(
                payoutWebhookData.providerStatus()
        );
        lockedWithdrawal.setProviderRawResponse(
                payoutWebhookData.rawResponse()
        );

        String normalizedEventType =
                normalize(payoutWebhookData.eventType());

        String normalizedProviderStatus =
                normalize(payoutWebhookData.providerStatus());

        if ("PAYOUT_SUCCESS".equals(normalizedEventType)
                || SUCCESS_STATUSES.contains(normalizedProviderStatus)) {
            settleSuccessfulWithdrawal(
                    lockedWithdrawal,
                    "Nomba payout success webhook"
            );
            return;
        }

        if ("PAYOUT_REFUND".equals(normalizedEventType)
                || REVERSED_STATUSES.contains(normalizedProviderStatus)) {
            releaseWithdrawalHold(
                    lockedWithdrawal,
                    "Nomba payout refund webhook"
            );
            return;
        }

        if ("PAYOUT_FAILED".equals(normalizedEventType)
                || FAILED_STATUSES.contains(normalizedProviderStatus)) {
            releaseWithdrawalHold(
                    lockedWithdrawal,
                    "Nomba payout failed webhook"
            );
            return;
        }

        markProcessing(
                lockedWithdrawal,
                "Payout webhook did not contain a final status"
        );
    }

    @Override
    @Transactional
    public void reconcileProcessingWithdrawal(
            UUID withdrawalId
    ) {
        MerchantWithdrawal withdrawal = lockWithdrawal(withdrawalId);

        if (withdrawal.getStatus() != MerchantWithdrawalStatus.PROCESSING) {
            return;
        }

        String transactionRef = resolveTransactionRefForRequery(withdrawal);

        if (!hasText(transactionRef)) {
            markManualReview(
                    withdrawal,
                    "Cannot requery withdrawal because provider transaction reference is missing"
            );
            return;
        }

        NombaTransferStatusResult statusResult =
                transferStatusGateway.requerySubAccountTransfer(transactionRef);

        applyTransferStatusResult(
                withdrawal.getId(),
                statusResult
        );
    }

    @Override
    @Transactional
    public void applyTransferStatusResult(
            UUID withdrawalId,
            NombaTransferStatusResult statusResult
    ) {
        MerchantWithdrawal withdrawal = lockWithdrawal(withdrawalId);

        if (isTerminal(withdrawal)) {
            return;
        }

        if (statusResult == null || !statusResult.found()) {
            markProcessing(
                    withdrawal,
                    "Transfer status not found during requery"
            );
            return;
        }

        if (hasText(statusResult.transferId())) {
            withdrawal.setProviderTransferId(statusResult.transferId());
        }

        withdrawal.setProviderStatus(statusResult.status());
        withdrawal.setProviderRawResponse(statusResult.rawResponse());

        if (statusResult.successful()) {
            settleSuccessfulWithdrawal(
                    withdrawal,
                    "Nomba transfer requery"
            );
            return;
        }

        if (statusResult.reversed() || statusResult.failed()) {
            releaseWithdrawalHold(
                    withdrawal,
                    "Nomba transfer requery returned status: " + statusResult.status()
            );
            return;
        }

        if (statusResult.pending()) {
            markProcessing(
                    withdrawal,
                    null
            );
            return;
        }

        String normalizedStatus = normalize(statusResult.status());

        if (PENDING_STATUSES.contains(normalizedStatus)) {
            markProcessing(
                    withdrawal,
                    null
            );
            return;
        }

        markProcessing(
                withdrawal,
                "Unexpected Nomba requery status: " + statusResult.status()
        );
    }

    private MerchantWithdrawal findWithdrawalForWebhook(
            NombaPayoutWebhookData payoutWebhookData
    ) {
        if (hasText(payoutWebhookData.merchantTxRef())) {
            return withdrawalRepository
                    .findByMerchantTxRef(payoutWebhookData.merchantTxRef())
                    .orElse(null);
        }

        if (hasText(payoutWebhookData.transferId())) {
            return withdrawalRepository
                    .findByProviderTransferId(payoutWebhookData.transferId())
                    .orElse(null);
        }

        return null;
    }

    private MerchantWithdrawal lockWithdrawal(UUID withdrawalId) {
        return withdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Merchant withdrawal not found"
                ));
    }

    private void settleSuccessfulWithdrawal(
            MerchantWithdrawal withdrawal,
            String source
    ) {
        if (hasText(withdrawal.getSettlementLedgerTransactionRef())) {
            withdrawal.setStatus(MerchantWithdrawalStatus.SUCCEEDED);
            withdrawal.setSucceededAt(
                    withdrawal.getSucceededAt() == null
                            ? Instant.now()
                            : withdrawal.getSucceededAt()
            );
            withdrawal.setFailureReason(null);
            return;
        }

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
                "Merchant withdrawal settled successfully. source={}, tenantId={}, withdrawalId={}, merchantTxRef={}, amount={}, currency={}",
                source,
                withdrawal.getTenant().getId(),
                withdrawal.getId(),
                withdrawal.getMerchantTxRef(),
                withdrawal.getAmount(),
                withdrawal.getCurrency()
        );
    }

    private void releaseWithdrawalHold(
            MerchantWithdrawal withdrawal,
            String reason
    ) {
        if (hasText(withdrawal.getReversalLedgerTransactionRef())) {
            withdrawal.setStatus(MerchantWithdrawalStatus.REVERSED);
            withdrawal.setReversedAt(
                    withdrawal.getReversedAt() == null
                            ? Instant.now()
                            : withdrawal.getReversedAt()
            );
            withdrawal.setFailureReason(reason);
            return;
        }

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
                "Merchant withdrawal hold released. tenantId={}, withdrawalId={}, merchantTxRef={}, reason={}",
                withdrawal.getTenant().getId(),
                withdrawal.getId(),
                withdrawal.getMerchantTxRef(),
                reason
        );
    }

    private void markProcessing(
            MerchantWithdrawal withdrawal,
            String reason
    ) {
        withdrawal.setStatus(MerchantWithdrawalStatus.PROCESSING);
        withdrawal.setFailureReason(reason);
        withdrawal.setNextAttemptAt(null);

        log.info(
                "Merchant withdrawal remains processing. withdrawalId={}, merchantTxRef={}, reason={}",
                withdrawal.getId(),
                withdrawal.getMerchantTxRef(),
                reason
        );
    }

    private void markManualReview(
            MerchantWithdrawal withdrawal,
            String reason
    ) {
        withdrawal.setStatus(MerchantWithdrawalStatus.MANUAL_REVIEW);
        withdrawal.setFailureReason(reason);
        withdrawal.setNextAttemptAt(null);

        log.warn(
                "Merchant withdrawal moved to manual review. withdrawalId={}, merchantTxRef={}, reason={}",
                withdrawal.getId(),
                withdrawal.getMerchantTxRef(),
                reason
        );
    }

    private boolean isTerminal(MerchantWithdrawal withdrawal) {
        return withdrawal.getStatus() == MerchantWithdrawalStatus.SUCCEEDED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.REVERSED
                || withdrawal.getStatus() == MerchantWithdrawalStatus.FAILED;
    }

    private String resolveTransactionRefForRequery(
            MerchantWithdrawal withdrawal
    ) {
        if (hasText(withdrawal.getProviderTransferId())) {
            return withdrawal.getProviderTransferId();
        }

        return withdrawal.getMerchantTxRef();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}