package com.markbay.subscription_engine.eventoutbox.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxStatus;
import com.markbay.subscription_engine.eventoutbox.repository.EventOutboxRepository;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventOutboxServiceImpl implements EventOutboxService {

    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public EventOutbox recordEvent(CreateEventOutboxCommand command) {
        validate(command);

        return eventOutboxRepository.findByEventReference(command.eventReference())
                .orElseGet(() -> createEvent(command));
    }

    private EventOutbox createEvent(CreateEventOutboxCommand command) {
        String payloadJson = toJson(command.payload());

        EventOutbox event = EventOutbox.builder()
                .tenant(command.tenant())
                .eventType(command.eventType())
                .eventReference(command.eventReference())
                .aggregateType(command.aggregateType())
                .aggregateId(command.aggregateId())
                .payload(payloadJson)
                .status(EventOutboxStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(10)
                .nextAttemptAt(Instant.now())
                .build();

        EventOutbox savedEvent = eventOutboxRepository.save(event);

        log.info(
                "Outbox event recorded. tenantId={}, eventId={}, eventType={}, eventReference={}",
                savedEvent.getTenant().getId(),
                savedEvent.getId(),
                savedEvent.getEventType().value(),
                savedEvent.getEventReference()
        );

        return savedEvent;
    }

    private void validate(CreateEventOutboxCommand command) {
        if (command == null) {
            throw new BadRequestException("Outbox event command is required");
        }

        if (command.tenant() == null) {
            throw new BadRequestException("Outbox event tenant is required");
        }

        if (command.eventType() == null) {
            throw new BadRequestException("Outbox event type is required");
        }

        if (!hasText(command.eventReference())) {
            throw new BadRequestException("Outbox event reference is required");
        }

        if (!hasText(command.aggregateType())) {
            throw new BadRequestException("Outbox aggregate type is required");
        }

        if (!hasText(command.aggregateId())) {
            throw new BadRequestException("Outbox aggregate ID is required");
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new BadRequestException("Invalid outbox event payload");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}