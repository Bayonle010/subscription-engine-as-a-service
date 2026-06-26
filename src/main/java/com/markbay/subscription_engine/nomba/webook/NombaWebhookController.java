//package com.markbay.subscription_engine.nomba.webook;
//
//import com.markbay.subscription_engine.common.response.ApiResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@Slf4j
//@RequiredArgsConstructor
//@RestController
//@RequestMapping("/webhooks/nomba")
//public class NombaWebhookController {
//
//    @PostMapping
//    public ResponseEntity<Void> handleWebhook(
//            @RequestHeader(value = "nomba-signature",           required = false) String signature,
//            @RequestHeader(value = "nomba-sig-value",           required = false) String sigValue,
//            @RequestHeader(value = "nomba-signature-algorithm", required = false) String algorithm,
//            @RequestHeader(value = "nomba-signature-version",   required = false) String version,
//            @RequestHeader(value = "nomba-timestamp",           required = false) String timestamp,
//            @RequestBody String rawBody
//    ) {
//        // Nomba sends the signature in both nomba-signature and nomba-sig-value.
//        // Prefer nomba-signature; fall back to nomba-sig-value if absent.
//        String resolvedSignature = (signature != null && !signature.isBlank()) ? signature : sigValue;
//
//        log.warn("Nomba webhook received | algorithm={} version={} timestamp={}", algorithm, version, timestamp);
//
//        boolean valid = signatureVerifier.isValid(rawBody, resolvedSignature, algorithm, version, timestamp);
//
//        if (!valid) {
//            log.warn("Nomba webhook signature validation failed");
//            return ResponseEntity.status(401).build(); // 401
//        }
//
//        webhookService.process(rawBody);
//        return ResponseEntity.ok().build();
//    }
//
//}
