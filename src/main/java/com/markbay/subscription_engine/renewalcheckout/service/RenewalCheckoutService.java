package com.markbay.subscription_engine.renewalcheckout.service;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.renewalcheckout.entity.RenewalCheckoutSession;
import com.markbay.subscription_engine.subscription.entity.Subscription;

public interface RenewalCheckoutService {

    RenewalCheckoutSession createCheckoutForPaymentMethodUpdateRenewal(
            Subscription subscription,
            Invoice invoice
    );

    void markRenewalCheckoutFailed(
            String orderReference,
            String reason
    );
}