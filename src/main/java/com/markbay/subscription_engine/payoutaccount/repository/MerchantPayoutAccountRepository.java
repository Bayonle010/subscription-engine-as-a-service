package com.markbay.subscription_engine.payoutaccount.repository;

import com.markbay.subscription_engine.payoutaccount.entity.MerchantPayoutAccount;
import com.markbay.subscription_engine.payoutaccount.enums.PayoutAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantPayoutAccountRepository
        extends JpaRepository<MerchantPayoutAccount, UUID> {

    Optional<MerchantPayoutAccount> findByIdAndTenant_Id(
            UUID id,
            UUID tenantId
    );

    List<MerchantPayoutAccount> findAllByTenant_IdAndStatusOrderByCreatedAtDesc(
            UUID tenantId,
            PayoutAccountStatus status
    );
}