package com.markbay.subscription_engine.nomba.support;

import com.markbay.subscription_engine.nomba.exception.NombaApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class NombaRestClientErrorHandler {

    public void handle(
            HttpRequest request,
            ClientHttpResponse response
    ) throws IOException {
        int statusCode = response.getStatusCode().value();

        String body = new String(
                response.getBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        log.error(
                "Nomba upstream error. method={}, path={}, status={}, response={}",
                request.getMethod(),
                request.getURI().getPath(),
                statusCode,
                truncate(body)
        );

        throw new NombaApiException(
                "Nomba upstream error " + statusCode + ": " + body
        );
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }

        int maxLength = 1000;

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "...";
    }
}