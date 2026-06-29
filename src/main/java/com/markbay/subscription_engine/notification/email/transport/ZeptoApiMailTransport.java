package com.markbay.subscription_engine.notification.email.transport;

import com.markbay.subscription_engine.notification.email.dto.RenderedEmail;
import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;
import com.markbay.subscription_engine.notification.email.exception.MailDeliveryException;
import com.markbay.subscription_engine.notification.email.transport.zepto.ZeptoMailAddress;
import com.markbay.subscription_engine.notification.email.transport.zepto.ZeptoMailRecipient;
import com.markbay.subscription_engine.notification.email.transport.zepto.ZeptoSendEmailRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "mail.transport", havingValue = "api", matchIfMissing = true)
public class ZeptoApiMailTransport implements MailTransport {

    private final RestClient zeptoMailRestClient;
    private final String defaultFromAddress;
    private final String defaultFromName;
    private final String replyTo;
    private final boolean includeText;

    public ZeptoApiMailTransport(
            @Qualifier("zeptomailRestClient") RestClient zeptoMailRestClient,
            @Value("${zeptomail.from.address}") String defaultFromAddress,
            @Value("${zeptomail.from.name:Subscription Engine}") String defaultFromName,
            @Value("${zeptomail.reply-to:}") String replyTo,
            @Value("${zeptomail.include-text:true}") boolean includeText
    ) {
        this.zeptoMailRestClient = zeptoMailRestClient;
        this.defaultFromAddress = defaultFromAddress;
        this.defaultFromName = defaultFromName;
        this.replyTo = replyTo;
        this.includeText = includeText;
    }

    @Override
    public void send(
            SendEmailCommand command,
            RenderedEmail renderedEmail
    ) {
        try {
            ZeptoSendEmailRequest request = buildRequest(command, renderedEmail);

            log.info(
                    "Sending email through ZeptoMail. recipients={}, subject={}",
                    command.to(),
                    renderedEmail.subject()
            );

            String responseBody = zeptoMailRestClient.post()
                    .uri("/email")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::handleZeptoError)
                    .body(String.class);

            log.info(
                    "Email sent through ZeptoMail successfully. recipients={}, response={}",
                    command.to(),
                    truncate(responseBody)
            );

        } catch (MailDeliveryException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error(
                    "ZeptoMail network error. recipients={}, message={}",
                    command.to(),
                    exception.getMessage()
            );

            throw new MailDeliveryException("ZeptoMail network error", exception);

        } catch (Exception exception) {
            log.error(
                    "Unexpected email delivery error. recipients={}",
                    command.to(),
                    exception
            );

            throw new MailDeliveryException("Unexpected email delivery error", exception);
        }
    }

    private ZeptoSendEmailRequest buildRequest(
            SendEmailCommand command,
            RenderedEmail renderedEmail
    ) {
        String fromAddress = hasText(command.fromAddress())
                ? command.fromAddress().trim()
                : defaultFromAddress;

        String fromName = hasText(command.fromName())
                ? command.fromName().trim()
                : defaultFromName;

        return ZeptoSendEmailRequest.builder()
                .from(new ZeptoMailAddress(fromAddress, fromName))
                .to(toRecipients(command.to()))
                .cc(toRecipients(command.cc()))
                .bcc(toRecipients(command.bcc()))
                .subject(renderedEmail.subject())
                .htmlBody(renderedEmail.htmlBody())
                .textBody(includeText ? renderedEmail.textBody() : null)
                .replyTo(resolveReplyTo())
                .build();
    }

    private List<ZeptoMailRecipient> toRecipients(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }

        return emails.stream()
                .filter(this::hasText)
                .map(String::trim)
                .map(address -> new ZeptoMailRecipient(
                        new ZeptoMailAddress(address, null)
                ))
                .toList();
    }

    private List<ZeptoMailAddress> resolveReplyTo() {
        if (!hasText(replyTo)) {
            return List.of();
        }

        return List.of(new ZeptoMailAddress(replyTo.trim(), null));
    }

    private void handleZeptoError(
            org.springframework.http.HttpRequest request,
            ClientHttpResponse response
    ) throws IOException {
        String body = new String(
                response.getBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        log.error(
                "ZeptoMail API error. method={}, path={}, status={}, response={}",
                request.getMethod(),
                request.getURI().getPath(),
                response.getStatusCode().value(),
                truncate(body)
        );

        throw new MailDeliveryException(
                "ZeptoMail API error: " + response.getStatusCode().value()
        );
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }

        if (value.length() <= 500) {
            return value;
        }

        return value.substring(0, 500) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}