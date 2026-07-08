package com.markbay.subscription_engine.paymentmethod.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.paymentmethod.enums.PaymentAuthorizationStatus;
import com.markbay.subscription_engine.paymentmethod.enums.PaymentMethodType;
import com.markbay.subscription_engine.paymentmethod.repository.CustomerPaymentMethodRepository;
import com.markbay.subscription_engine.paymentmethod.service.CustomerPaymentMethodService;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerPaymentMethodServiceImpl implements CustomerPaymentMethodService {

    private final CustomerPaymentMethodRepository paymentMethodRepository;

    @Override
    public CustomerPaymentMethod findOrCreateCardPaymentMethod(
            Tenant tenant,
            Customer customer,
            NombaWebhookPaymentData paymentData,
            String providerRawData
    ) {
//        if (paymentData == null || !hasText(paymentData.tokenKey())) {
//            throw new BadRequestException(
//                    "Nomba tokenized card token is missing"
//            );
//        }

        return paymentMethodRepository.findByTenant_IdAndProviderTokenKey(
                        tenant.getId(),
                        paymentData.tokenKey()
                )
                .orElseGet(() -> createCardPaymentMethod(
                        tenant,
                        customer,
                        paymentData,
                        providerRawData
                ));
    }

    private CustomerPaymentMethod createCardPaymentMethod(
            Tenant tenant,
            Customer customer,
            NombaWebhookPaymentData paymentData,
            String providerRawData
    ) {
        CustomerPaymentMethod paymentMethod = CustomerPaymentMethod.builder()
                .tenant(tenant)
                .customer(customer)
                .type(PaymentMethodType.CARD)
                .status(PaymentAuthorizationStatus.ACTIVE)
                .provider("NOMBA")
                .providerTokenKey(paymentData.tokenKey())
                .cardBrand(paymentData.cardType())
                .cardLast4(paymentData.cardLast4())
                .expiryMonth(paymentData.expiryMonth())
                .expiryYear(paymentData.expiryYear())
                .reusable(true)
                .providerRawData(providerRawData)
                .build();

        return paymentMethodRepository.save(paymentMethod);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}