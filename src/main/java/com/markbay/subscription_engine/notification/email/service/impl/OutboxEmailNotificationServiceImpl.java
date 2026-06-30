package com.markbay.subscription_engine.notification.email.service.impl;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.notification.email.dto.EmailParam;
import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;
import com.markbay.subscription_engine.notification.email.entity.EmailNotificationLog;
import com.markbay.subscription_engine.notification.email.enums.EmailMessageType;
import com.markbay.subscription_engine.notification.email.enums.EmailNotificationStatus;
import com.markbay.subscription_engine.notification.email.repository.EmailNotificationLogRepository;
import com.markbay.subscription_engine.notification.email.service.EmailNotificationService;
import com.markbay.subscription_engine.notification.email.service.OutboxEmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEmailNotificationServiceImpl implements OutboxEmailNotificationService {

    private final ObjectMapper objectMapper;
    private final EmailNotificationService emailNotificationService;
    private final EmailNotificationLogRepository emailNotificationLogRepository;

    @Override
    @Transactional
    public void handle(EventOutbox event) {
        if (event.getEventType() == EventOutboxType.SUBSCRIPTION_ACTIVATED) {
            sendSubscriptionActivatedEmail(event);
            return;
        }

        if (event.getEventType() == EventOutboxType.PAYMENT_SUCCEEDED) {
            sendPaymentSucceededEmail(event);
        }

        if (event.getEventType() == EventOutboxType.PAYMENT_FAILED) {
            sendPaymentFailedEmail(event);
            return;
        }

        if (event.getEventType() == EventOutboxType.SUBSCRIPTION_CANCELLED) {
            sendSubscriptionCancelledEmail(event);
        }
    }

    private void sendSubscriptionActivatedEmail(EventOutbox event) {
        JsonNode payload = readPayload(event);

        String customerEmail = text(payload, "customerEmail");
        String customerName = text(payload, "customerName");
        String planName = text(payload, "planName");

        if (!hasText(customerEmail)) {
            log.warn(
                    "Skipping subscription activated email because customer email is missing. eventId={}",
                    event.getId()
            );
            return;
        }

        String subject = "Your subscription is active";

        String body = """
                Hi {{customerName}},
                
                Your subscription to {{planName}} is now active.
                
                Thank you.
                """;

        sendOnce(
                event,
                customerEmail,
                subject,
                body,
                List.of(
                        EmailParam.builder()
                                .name("customerName")
                                .value(hasText(customerName) ? customerName : "there")
                                .build(),
                        EmailParam.builder()
                                .name("planName")
                                .value(hasText(planName) ? planName : "your plan")
                                .build()
                )
        );
    }

    private void sendPaymentSucceededEmail(EventOutbox event) {
        JsonNode payload = readPayload(event);

        String customerEmail = text(payload, "customerEmail");
        String customerName = text(payload, "customerName");
        String amount = text(payload, "amount");
        String currency = text(payload, "currency");

        if (!hasText(customerEmail)) {
            log.warn(
                    "Skipping payment succeeded email because customer email is missing. eventId={}",
                    event.getId()
            );
            return;
        }

        String subject = "Payment successful";

        String body = """
                Hi {{customerName}},
                
                Your payment of {{amount}} {{currency}} was successful.
                
                Thank you.
                """;

        sendOnce(
                event,
                customerEmail,
                subject,
                body,
                List.of(
                        EmailParam.builder()
                                .name("customerName")
                                .value(hasText(customerName) ? customerName : "there")
                                .build(),
                        EmailParam.builder()
                                .name("amount")
                                .value(hasText(amount) ? amount : "")
                                .build(),
                        EmailParam.builder()
                                .name("currency")
                                .value(hasText(currency) ? currency : "")
                                .build()
                )
        );
    }

    private void sendOnce(
            EventOutbox event,
            String recipient,
            String subject,
            String body,
            List<EmailParam> params
    ) {
        boolean alreadySent = emailNotificationLogRepository
                .existsByOutboxEvent_IdAndRecipientAndEmailTypeAndStatus(
                        event.getId(),
                        recipient,
                        event.getEventType(),
                        EmailNotificationStatus.SENT
                );

        if (alreadySent) {
            log.info(
                    "Email already sent for outbox event. eventId={}, recipient={}, emailType={}",
                    event.getId(),
                    recipient,
                    event.getEventType()
            );

            return;
        }

        EmailNotificationLog emailLog = emailNotificationLogRepository
                .findByOutboxEvent_IdAndRecipientAndEmailType(
                        event.getId(),
                        recipient,
                        event.getEventType()
                )
                .orElseGet(() -> EmailNotificationLog.builder()
                        .tenant(event.getTenant())
                        .outboxEvent(event)
                        .emailType(event.getEventType())
                        .recipient(recipient)
                        .subject(subject)
                        .status(EmailNotificationStatus.PENDING)
                        .build()
                );

        try {
            emailNotificationService.sendNow(
                    SendEmailCommand.builder()
                            .to(List.of(recipient))
                            .messageType(EmailMessageType.TEXT)
                            .subject(subject)
                            .messageContentOrTemplateName(body)
                            .params(params)
                            .build()
            );

            emailLog.setStatus(EmailNotificationStatus.SENT);
            emailLog.setSentAt(Instant.now());
            emailLog.setFailureReason(null);

            emailNotificationLogRepository.save(emailLog);

            log.info(
                    "Outbox email sent. eventId={}, recipient={}, emailType={}",
                    event.getId(),
                    recipient,
                    event.getEventType()
            );

        } catch (Exception exception) {
            emailLog.setStatus(EmailNotificationStatus.FAILED);
            emailLog.setFailureReason(exception.getMessage());

            emailNotificationLogRepository.save(emailLog);

            log.error(
                    "Outbox email failed. eventId={}, recipient={}, emailType={}, reason={}",
                    event.getId(),
                    recipient,
                    event.getEventType(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void sendPaymentFailedEmail(EventOutbox event) {
        JsonNode payload = readPayload(event);

        String customerEmail = text(payload, "customerEmail");
        String customerName = text(payload, "customerName");
        String amount = text(payload, "amount");
        String currency = text(payload, "currency");
        String reason = text(payload, "reason");

        if (!hasText(customerEmail)) {
            log.warn(
                    "Skipping payment failed email because customer email is missing. eventId={}",
                    event.getId()
            );
            return;
        }

        String subject = "Payment failed";

        String body = """
            Hi {{customerName}},
            
            We could not process your payment of {{amount}} {{currency}}.
            
            Reason: {{reason}}
            
            We will retry the payment automatically during the grace period.
            
            Thank you.
            """;

        sendOnce(
                event,
                customerEmail,
                subject,
                body,
                List.of(
                        EmailParam.builder()
                                .name("customerName")
                                .value(hasText(customerName) ? customerName : "there")
                                .build(),
                        EmailParam.builder()
                                .name("amount")
                                .value(hasText(amount) ? amount : "")
                                .build(),
                        EmailParam.builder()
                                .name("currency")
                                .value(hasText(currency) ? currency : "")
                                .build(),
                        EmailParam.builder()
                                .name("reason")
                                .value(hasText(reason) ? reason : "Payment failed")
                                .build()
                )
        );
    }

    private void sendSubscriptionCancelledEmail(EventOutbox event) {
        JsonNode payload = readPayload(event);

        String customerEmail = text(payload, "customerEmail");
        String customerName = text(payload, "customerName");
        String planName = text(payload, "planName");

        if (!hasText(customerEmail)) {
            log.warn(
                    "Skipping subscription cancelled email because customer email is missing. eventId={}",
                    event.getId()
            );
            return;
        }

        String subject = "Subscription cancelled";

        String body = """
            Hi {{customerName}},
            
            Your subscription to {{planName}} has been cancelled because payment could not be collected.
            
            Thank you.
            """;

        sendOnce(
                event,
                customerEmail,
                subject,
                body,
                List.of(
                        EmailParam.builder()
                                .name("customerName")
                                .value(hasText(customerName) ? customerName : "there")
                                .build(),
                        EmailParam.builder()
                                .name("planName")
                                .value(hasText(planName) ? planName : "your plan")
                                .build()
                )
        );
    }

    private JsonNode readPayload(EventOutbox event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read outbox event payload", exception);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }

        String value = node.path(field).asText();

        if (!hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}