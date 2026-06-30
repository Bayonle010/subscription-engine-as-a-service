package com.markbay.subscription_engine.customerportal.service;

import com.markbay.subscription_engine.customerportal.dto.CustomerPortalOverviewResponse;
import com.markbay.subscription_engine.customerportal.dto.PaymentRescueCheckoutResponse;
import com.markbay.subscription_engine.customerportal.dto.PaymentRescueLinkResponse;
import com.markbay.subscription_engine.dunning.entity.DunningCase;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.subscription.entity.Subscription;

import java.util.UUID;

public interface PaymentRescueService {

    PaymentRescueLinkResponse createPaymentRescueLinkForInvoice(UUID invoiceId);

    PaymentRescueLinkResponse createPaymentRescueLink(
            Subscription subscription,
            Invoice invoice,
            DunningCase dunningCase
    );

    CustomerPortalOverviewResponse getPortalOverview(String rawToken);

    PaymentRescueCheckoutResponse createPaymentRescueCheckout(String rawToken);
}