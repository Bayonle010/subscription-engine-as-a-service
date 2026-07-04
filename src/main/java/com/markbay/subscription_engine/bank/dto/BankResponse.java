package com.markbay.subscription_engine.bank.dto;

import com.markbay.subscription_engine.bank.entity.NombaBank;

import java.util.UUID;

public record BankResponse(
        UUID id,
        String code,
        String name,
        String nipCode,
        String logo,
        String status
) {
    public static BankResponse from(NombaBank bank) {
        return new BankResponse(
                bank.getId(),
                bank.getCode(),
                bank.getName(),
                bank.getNipCode(),
                bank.getLogo(),
                bank.getStatus().name()
        );
    }
}