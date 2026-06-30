package com.markbay.subscription_engine.webhook.controller;

import com.markbay.subscription_engine.webhook.service.NombaWebhookReceiverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhooks/nomba")
@RequiredArgsConstructor
public class NombaWebhookController {

    private final NombaWebhookReceiverService webhookReceiverService;

    @PostMapping
    public ResponseEntity<Void> receiveNombaWebhook(
            @RequestHeader(value = "nomba-signature", required = false) String signature,
            @RequestHeader(value = "nomba-sig-value", required = false) String sigValue,
            @RequestHeader(value = "nomba-signature-algorithm", required = false) String algorithm,
            @RequestHeader(value = "nomba-signature-version", required = false) String version,
            @RequestHeader(value = "nomba-timestamp", required = false) String timestamp,
            @RequestBody String rawPayload
    ) {
        String resolvedSignature =
                hasText(signature) ? signature : sigValue;

        log.info(
                "Nomba webhook received. algorithm={}, version={}, timestamp={}",
                algorithm,
                version,
                timestamp
        );

        webhookReceiverService.receiveWebhook(
                rawPayload,
                resolvedSignature,
                algorithm,
                version,
                timestamp
        );

        return ResponseEntity.ok().build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}