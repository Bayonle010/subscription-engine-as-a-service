package com.markbay.subscription_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SubscriptionEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(SubscriptionEngineApplication.class, args);
	}

}
