package com.markbay.subscription_engine.bank.service;

import com.markbay.subscription_engine.bank.dto.BankResponse;

import java.util.List;

public interface BankService {

    List<BankResponse> syncBanksFromNomba();

    List<BankResponse> listBanksFromDatabase();
}