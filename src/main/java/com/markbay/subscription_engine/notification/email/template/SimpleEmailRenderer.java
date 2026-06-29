package com.markbay.subscription_engine.notification.email.template;

import com.markbay.subscription_engine.notification.email.dto.EmailParam;
import com.markbay.subscription_engine.notification.email.dto.RenderedEmail;
import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;
import com.markbay.subscription_engine.notification.email.enums.EmailMessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SimpleEmailRenderer implements EmailRenderer {

    private final EmailTemplateRegistry templateRegistry;

    @Override
    public RenderedEmail render(SendEmailCommand command) {
        String rawBody = resolveBody(command);
        Map<String, String> params = resolveParams(command);

        String textBody = replaceParams(rawBody, params);
        String subject = replaceParams(command.subject(), params);
        String htmlBody = toSimpleHtml(textBody);

        return new RenderedEmail(
                subject,
                textBody,
                htmlBody
        );
    }

    private String resolveBody(SendEmailCommand command) {
        if (command.messageType() == EmailMessageType.TEMPLATE) {
            return templateRegistry
                    .findTemplate(command.messageContentOrTemplateName())
                    .orElse(command.messageContentOrTemplateName());
        }

        return command.messageContentOrTemplateName();
    }

    private Map<String, String> resolveParams(SendEmailCommand command) {
        Map<String, String> params = new LinkedHashMap<>();

        params.put("currentYear", String.valueOf(Year.now().getValue()));

        for (EmailParam param : command.params()) {
            if (param == null) {
                continue;
            }

            if (hasText(param.name()) && hasText(param.value())) {
                params.put(param.name().trim(), param.value().trim());
            }
        }

        return params;
    }

    private String replaceParams(
            String value,
            Map<String, String> params
    ) {
        if (value == null) {
            return "";
        }

        String resolved = value;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            resolved = resolved.replace(
                    "{{" + entry.getKey() + "}}",
                    entry.getValue()
            );

            resolved = resolved.replace(
                    "${" + entry.getKey() + "}",
                    entry.getValue()
            );
        }

        return resolved;
    }

    private String toSimpleHtml(String textBody) {
        String escaped = escapeHtml(textBody);

        return escaped.replace("\n", "<br/>");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}