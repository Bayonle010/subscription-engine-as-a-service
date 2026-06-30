package com.markbay.subscription_engine.dunning.repository;

import com.markbay.subscription_engine.dunning.entity.DunningAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DunningAttemptRepository extends JpaRepository<DunningAttempt, UUID> {

    Optional<DunningAttempt> findByDunningCase_IdAndAttemptNumber(
            UUID dunningCaseId,
            int attemptNumber
    );
}