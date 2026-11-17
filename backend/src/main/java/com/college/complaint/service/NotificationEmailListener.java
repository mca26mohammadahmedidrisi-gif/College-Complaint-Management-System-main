package com.college.complaint.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEmailListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationEmailListener.class);
    private final EmailDeliveryService email;

    public NotificationEmailListener(EmailDeliveryService email) {
        this.email = email;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(NotificationMailEvent event) {
        if (!email.enabled()) return;
        try {
            email.sendNotification(event.recipient(), event.message());
        } catch (RuntimeException exception) {
            log.warn("CampusCare notification email could not be delivered");
        }
    }
}
