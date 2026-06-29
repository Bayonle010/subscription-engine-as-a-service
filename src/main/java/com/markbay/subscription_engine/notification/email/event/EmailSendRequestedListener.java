package com.markbay.subscription_engine.notification.email.event;

import com.markbay.subscription_engine.notification.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSendRequestedListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void handleEmailSendRequested(EmailSendRequestedEvent event) {
        try {
            emailService.send(event.command());

            log.info(
                    "Async email notification processed. recipients={}, subject={}",
                    event.command().to(),
                    event.command().subject()
            );

        } catch (Exception exception) {
            log.error(
                    "Async email notification failed. recipients={}, subject={}, reason={}",
                    event.command().to(),
                    event.command().subject(),
                    exception.getMessage(),
                    exception
            );
        }
    }
}