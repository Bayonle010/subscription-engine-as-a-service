package com.markbay.subscription_engine.bank.repository;

import com.markbay.subscription_engine.bank.entity.NombaBank;
import com.markbay.subscription_engine.bank.enums.BankStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NombaBankRepository extends JpaRepository<NombaBank, UUID> {

    Optional<NombaBank> findByCode(String code);

    List<NombaBank> findAllByStatusOrderByNameAsc(BankStatus status);
}