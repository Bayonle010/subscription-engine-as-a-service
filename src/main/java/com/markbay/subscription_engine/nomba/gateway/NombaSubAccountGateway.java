package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.request.NombaCreateSubAccountRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaApiResponse;
import com.markbay.subscription_engine.nomba.dto.response.NombaSubAccountBalanceResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaSubAccountResult;
import com.markbay.subscription_engine.nomba.exception.NombaApiException;
import com.markbay.subscription_engine.nomba.service.NombaAuthService;
import com.markbay.subscription_engine.nomba.support.NombaRestClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

@Slf4j
@Component
public class NombaSubAccountGateway {

    private final RestClient nombaRestClient;
    private final NombaAuthService nombaAuthService;
    private final NombaRestClientErrorHandler nombaErrorHandler;

    public NombaSubAccountGateway(
            @Qualifier("nombaRestClient") RestClient nombaRestClient,
            NombaAuthService nombaAuthService,
            NombaRestClientErrorHandler nombaErrorHandler
    ) {
        this.nombaRestClient = nombaRestClient;
        this.nombaAuthService = nombaAuthService;
        this.nombaErrorHandler = nombaErrorHandler;
    }

    public NombaSubAccountResult createSubAccount(
            String accountName,
            String accountRef
    ) {
        try {
            log.info(
                    "Creating Nomba sub-account. accountName={}, accountRef={}",
                    accountName,
                    accountRef
            );

            NombaCreateSubAccountRequest request =
                    new NombaCreateSubAccountRequest(accountName, accountRef);

            NombaApiResponse<JsonNode> response = nombaRestClient.post()
                    .uri("/accounts/sub-accounts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + nombaAuthService.getAccessToken())
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, nombaErrorHandler::handle)
                    .body(new ParameterizedTypeReference<>() {
                    });

            validateNombaResponse(response, "create sub-account");

            JsonNode data = response.data();

            String providerAccountId = firstText(
                    data,
                    "id",
                    "accountId",
                    "account_id"
            );

            String resolvedAccountName = firstText(
                    data,
                    "accountName",
                    "account_name"
            );

            String resolvedAccountRef = firstText(
                    data,
                    "accountRef",
                    "account_ref"
            );

            if (providerAccountId == null || providerAccountId.isBlank()) {
                log.warn(
                        "Nomba create sub-account succeeded but provider account ID was not found. accountRef={}, response={}",
                        accountRef,
                        response
                );
            }

            log.info(
                    "Nomba sub-account created. providerAccountId={}, accountRef={}",
                    providerAccountId,
                    resolvedAccountRef == null ? accountRef : resolvedAccountRef
            );

            return new NombaSubAccountResult(
                    providerAccountId,
                    resolvedAccountName == null ? accountName : resolvedAccountName,
                    resolvedAccountRef == null ? accountRef : resolvedAccountRef,
                    response.toString()
            );

        } catch (NombaApiException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error(
                    "Nomba create sub-account network error. accountRef={}, message={}",
                    accountRef,
                    exception.getMessage()
            );

            throw new NombaApiException("Nomba create sub-account network error", exception);

        } catch (Exception exception) {
            log.error(
                    "Nomba create sub-account failed. accountRef={}",
                    accountRef,
                    exception
            );

            throw new NombaApiException("Nomba create sub-account failed", exception);
        }
    }

    public NombaSubAccountBalanceResult fetchSubAccountBalance(
            String providerAccountId
    ) {
        try {
            log.info(
                    "Fetching Nomba sub-account balance. providerAccountId={}",
                    providerAccountId
            );

            NombaApiResponse<JsonNode> response = nombaRestClient.get()
                    .uri("/accounts/sub-accounts/{id}/balance", providerAccountId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + nombaAuthService.getAccessToken())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, nombaErrorHandler::handle)
                    .body(new ParameterizedTypeReference<>() {
                    });

            validateNombaResponse(response, "fetch sub-account balance");

            JsonNode data = response.data();

            BigDecimal availableBalance = firstDecimal(
                    data,
                    "availableBalance",
                    "available_balance",
                    "balance"
            );

            String currency = firstText(
                    data,
                    "currency"
            );

            log.info(
                    "Nomba sub-account balance fetched. providerAccountId={}, availableBalance={}, currency={}",
                    providerAccountId,
                    availableBalance,
                    currency
            );

            return new NombaSubAccountBalanceResult(
                    providerAccountId,
                    availableBalance,
                    currency,
                    response.toString()
            );

        } catch (NombaApiException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error(
                    "Nomba fetch sub-account balance network error. providerAccountId={}, message={}",
                    providerAccountId,
                    exception.getMessage()
            );

            throw new NombaApiException("Nomba fetch sub-account balance network error", exception);

        } catch (Exception exception) {
            log.error(
                    "Nomba fetch sub-account balance failed. providerAccountId={}",
                    providerAccountId,
                    exception
            );

            throw new NombaApiException("Nomba fetch sub-account balance failed", exception);
        }
    }

    private void validateNombaResponse(
            NombaApiResponse<JsonNode> response,
            String operation
    ) {
        if (response == null) {
            throw new NombaApiException("Nomba " + operation + " response is null");
        }

        if (!response.isSuccessful()) {
            throw new NombaApiException(
                    "Nomba " + operation + " failed: " + response.description()
            );
        }

        if (response.data() == null || response.data().isNull()) {
            throw new NombaApiException("Nomba " + operation + " response missing data");
        }
    }

    private String firstText(JsonNode root, String... paths) {
        if (root == null || paths == null) {
            return null;
        }

        for (String path : paths) {
            JsonNode node = readPath(root, path);

            if (node != null && !node.isNull() && node.isValueNode()) {
                String value = node.asText();

                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }

        return null;
    }

    private BigDecimal firstDecimal(JsonNode root, String... paths) {
        if (root == null || paths == null) {
            return null;
        }

        for (String path : paths) {
            JsonNode node = readPath(root, path);

            if (node != null && !node.isNull() && node.isValueNode()) {
                try {
                    return new BigDecimal(node.asText());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        return null;
    }

    private JsonNode readPath(JsonNode root, String path) {
        String[] parts = path.split("\\.");

        JsonNode current = root;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            current = current.get(part);
        }

        return current;
    }
}