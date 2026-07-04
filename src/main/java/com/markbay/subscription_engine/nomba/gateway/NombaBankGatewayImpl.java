package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.nomba.dto.request.NombaBankAccountLookupRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaBankAccountLookupResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaBankResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NombaBankGatewayImpl implements NombaBankGateway {

    @Qualifier("nombaParentRestClient")
    private final RestClient nombaParentRestClient;

    private final ObjectMapper objectMapper;

    @Value("${payment.nomba.account-id}")
    private String nombaParentAccountId;

    @Override
    public List<NombaBankResult> fetchBanks() {
        JsonNode response = nombaParentRestClient
                .get()
                .uri("/v1/transfers/banks")
                .header("accountId", nombaParentAccountId)
                .retrieve()
                .body(JsonNode.class);

        JsonNode data = response == null ? null : response.path("data");

        JsonNode banksNode;

        if (data != null && data.has("results")) {
            banksNode = data.path("results");
        } else {
            banksNode = data;
        }

        List<NombaBankResult> banks = new ArrayList<>();

        if (banksNode != null && banksNode.isArray()) {
            for (JsonNode bankNode : banksNode) {
                banks.add(new NombaBankResult(
                        text(bankNode, "code"),
                        text(bankNode, "name"),
                        text(bankNode, "nipCode"),
                        text(bankNode, "logo")
                ));
            }
        }

        return banks;
    }

    @Override
    public NombaBankAccountLookupResult lookupBankAccount(
            String accountNumber,
            String bankCode
    ) {
        JsonNode response = nombaParentRestClient
                .post()
                .uri("/v1/transfers/bank/lookup")
                .header("accountId", nombaParentAccountId)
                .body(new NombaBankAccountLookupRequest(accountNumber, bankCode))
                .retrieve()
                .body(JsonNode.class);

        String rawResponse = toRawJson(response);

        if (response == null || !"00".equals(text(response, "code"))) {
            throw new BadRequestException("Bank account lookup failed");
        }

        JsonNode data = response.path("data");

        return new NombaBankAccountLookupResult(
                true,
                text(data, "accountNumber"),
                text(data, "accountName"),
                rawResponse
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }

        return node.path(field).asText();
    }

    private String toRawJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }
}