package com.markbay.subscription_engine.renewalcheckout.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "renewal-checkout")
public class RenewalCheckoutProperties {

    private int checkoutExpiryHours = 24;
}