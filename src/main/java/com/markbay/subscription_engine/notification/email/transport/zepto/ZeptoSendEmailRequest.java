package com.markbay.subscription_engine.notification.email.transport.zepto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ZeptoSendEmailRequest(
        ZeptoMailAddress from,
        List<ZeptoMailRecipient> to,
        List<ZeptoMailRecipient> cc,
        List<ZeptoMailRecipient> bcc,
        String subject,

        @JsonProperty("htmlbody")
        String htmlBody,

        @JsonProperty("textbody")
        String textBody,

        @JsonProperty("reply_to")
        List<ZeptoMailAddress> replyTo
) {
}