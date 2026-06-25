package com.markbay.subscription_engine.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory(
            @Value("${http.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${http.response-timeout-ms:15000}") int responseTimeoutMs
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_2)
                .build();

        JdkClientHttpRequestFactory jdkFactory =
                new JdkClientHttpRequestFactory(httpClient);

        jdkFactory.setReadTimeout(Duration.ofMillis(responseTimeoutMs));

        return new BufferingClientHttpRequestFactory(jdkFactory);
    }

    @Bean
    public RestClient.Builder restClientBuilder(
            ClientHttpRequestFactory clientHttpRequestFactory
    ) {
        return RestClient.builder()
                .requestFactory(clientHttpRequestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    @Bean
    @Qualifier("nombaRestClient")
    public RestClient nombaRestClient(
            RestClient.Builder builder,
            @Value("${payment.nomba.base-url}") String baseUrl,
            @Value("${payment.nomba.account-id}") String accountId
    ) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("accountId", accountId)
                .build();
    }
}