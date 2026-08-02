package com.smartwork.report.infrastructure;

import java.util.List;

/** The one thing the outbox relay needs from an email transport. */
public interface EmailSender {

    /** Throws on any failure to send; the relay is what decides retry vs. dead-letter. */
    void send(List<String> recipients, String subject, String htmlBody) throws EmailDeliveryException;

    class EmailDeliveryException extends Exception {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
