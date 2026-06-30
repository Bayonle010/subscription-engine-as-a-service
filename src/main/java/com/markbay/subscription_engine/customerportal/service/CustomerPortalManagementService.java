package com.markbay.subscription_engine.customerportal.service;

import com.markbay.subscription_engine.customerportal.dto.CustomerPortalActionResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalInvoiceResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalManagementLinkResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalSubscriptionResponse;

import java.util.List;
import java.util.UUID;

public interface CustomerPortalManagementService {

    CustomerPortalManagementLinkResponse createManagementLink(UUID subscriptionId);

    CustomerPortalSubscriptionResponse getSubscription(String rawToken);

    List<CustomerPortalInvoiceResponse> listInvoices(String rawToken);

    CustomerPortalActionResponse cancelAtPeriodEnd(String rawToken);

    CustomerPortalActionResponse cancelNow(String rawToken);

    CustomerPortalActionResponse resumeCancellation(String rawToken);
}