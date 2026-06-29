package com.markbay.subscription_engine.notification.email.entity;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.notification.email.enums.EmailNotificationStatus;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "email_notification_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_email_notification_event_recipient_type",
                        columnNames = {"outbox_event_id", "recipient", "email_type"}
                )
        },
        indexes = {
                @Index(name = "idx_email_notifications_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_email_notifications_outbox_event_id", columnList = "outbox_event_id"),
                @Index(name = "idx_email_notifications_status", columnList = "status"),
                @Index(name = "idx_email_notifications_created_at", columnList = "created_at")
        }
)
public class EmailNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbox_event_id", nullable = false)
    private EventOutbox outboxEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 80)
    private EventOutboxType emailType;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EmailNotificationStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = EmailNotificationStatus.PENDING;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}