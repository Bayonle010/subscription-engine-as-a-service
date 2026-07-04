package com.markbay.subscription_engine.bank.service.impl;

import com.markbay.subscription_engine.bank.dto.BankResponse;
import com.markbay.subscription_engine.bank.entity.NombaBank;
import com.markbay.subscription_engine.bank.enums.BankStatus;
import com.markbay.subscription_engine.bank.repository.NombaBankRepository;
import com.markbay.subscription_engine.bank.service.BankService;
import com.markbay.subscription_engine.nomba.dto.response.NombaBankResult;
import com.markbay.subscription_engine.nomba.gateway.NombaBankGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankServiceImpl implements BankService {

    private final NombaBankGateway nombaBankGateway;
    private final NombaBankRepository nombaBankRepository;

    @Override
    @Transactional
    public List<BankResponse> syncBanksFromNomba() {
        List<NombaBankResult> nombaBanks = nombaBankGateway.fetchBanks();

        Instant syncedAt = Instant.now();

        for (NombaBankResult bankResult : nombaBanks) {
            if (!hasText(bankResult.code()) || !hasText(bankResult.name())) {
                log.warn(
                        "Skipping Nomba bank with missing code or name. code={}, name={}",
                        bankResult.code(),
                        bankResult.name()
                );
                continue;
            }

            NombaBank bank = nombaBankRepository.findByCode(bankResult.code())
                    .orElseGet(() -> NombaBank.builder()
                            .code(bankResult.code())
                            .build());

            bank.setName(bankResult.name());
            bank.setNipCode(bankResult.nipCode());
            bank.setLogo(bankResult.logo());
            bank.setStatus(BankStatus.ACTIVE);
            bank.setLastSyncedAt(syncedAt);

            nombaBankRepository.save(bank);
        }

        log.info("Nomba banks synced successfully. count={}", nombaBanks.size());

        return listBanksFromDatabase();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankResponse> listBanksFromDatabase() {
        return nombaBankRepository
                .findAllByStatusOrderByNameAsc(BankStatus.ACTIVE)
                .stream()
                .map(BankResponse::from)
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}