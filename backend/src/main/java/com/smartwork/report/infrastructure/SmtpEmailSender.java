package com.smartwork.report.infrastructure;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Real SMTP delivery via Spring's {@link JavaMailSender} (configured through the standard
 * {@code spring.mail.*} properties — see {@code application.yml} and
 * {@code docker-compose.yml}'s {@code mailhog} service for local/demo use).
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${smartwork.report.mail-from:no-reply@smartwork.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(List<String> recipients, String subject, String htmlBody) throws EmailDeliveryException {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new EmailDeliveryException("SMTP delivery failed: " + e.getMessage(), e);
        }
    }
}
