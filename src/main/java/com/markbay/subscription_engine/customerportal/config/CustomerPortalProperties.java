package com.markbay.subscription_engine.customerportal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "customer-portal")
public class CustomerPortalProperties {

    private String publicBaseUrl = "http://localhost:8082";

    private int rescueLinkExpiryHours = 168;

    private int managementLinkExpiryHours = 720;
}