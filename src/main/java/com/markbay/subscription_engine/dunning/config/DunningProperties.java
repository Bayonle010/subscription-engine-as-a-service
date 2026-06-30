package com.markbay.subscription_engine.dunning.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dunning")
public class DunningProperties {

    private int gracePeriodDays = 7;

    private int maxRetryAttempts = 3;

    private List<Integer> retryDelaysHours = new ArrayList<>(List.of(24, 72, 168));

    private boolean cancelAfterFinalFailure = true;
}