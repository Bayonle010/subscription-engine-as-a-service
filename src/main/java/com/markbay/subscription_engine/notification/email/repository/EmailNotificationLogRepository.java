package com.markbay.subscription_engine.notification.email.repository;

import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.notification.email.entity.EmailNotificationLog;
import com.markbay.subscription_engine.notification.email.enums.EmailNotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailNotificationLogRepository
        extends JpaRepository<EmailNotificationLog, UUID> {

    Optional<EmailNotificationLog> findByOutboxEvent_IdAndRecipientAndEmailType(
            UUID outboxEventId,
            String recipient,
            EventOutboxType emailType
    );

    boolean existsByOutboxEvent_IdAndRecipientAndEmailTypeAndStatus(
            UUID outboxEventId,
            String recipient,
            EventOutboxType emailType,
            EmailNotificationStatus status
    );
}