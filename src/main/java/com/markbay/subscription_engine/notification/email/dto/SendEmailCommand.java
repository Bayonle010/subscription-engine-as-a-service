package com.markbay.subscription_engine.notification.email.dto;

import com.markbay.subscription_engine.notification.email.enums.EmailMessageType;
import lombok.Builder;

import java.util.List;

@Builder
public record SendEmailCommand(
        List<String> to,
        List<String> cc,
        List<String> bcc,
        EmailMessageType messageType,
        String messageContentOrTemplateName,
        String subject,
        String fromAddress,
        String fromName,
        List<EmailParam> params
) {
    public SendEmailCommand {
        to = safeList(to);
        cc = safeList(cc);
        bcc = safeList(bcc);
        params = safeList(params);

        if (messageType == null) {
            messageType = EmailMessageType.TEXT;
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return List.copyOf(values);
    }
}