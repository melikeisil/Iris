package com.smartwork.common.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Redacts credentials before they reach an appender.
 *
 * <p>Wired at the encoder level rather than at call sites, so it also covers third-party
 * libraries that helpfully log request headers.
 */
public class SecretMaskingConverter extends MessageConverter {

    private static final String MASK = "***REDACTED***";

    private static final Pattern[] PATTERNS = {
            // Authorization headers of any scheme
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(bearer|basic)\\s+\\S+"),
            // Bare JWTs
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
            // Atlassian API tokens and OAuth refresh tokens
            Pattern.compile("(?i)\\b(ATATT|ATCTT)[A-Za-z0-9_\\-=.]{16,}\\b"),
            // Anything that names itself a secret
            Pattern.compile("(?i)((?:api[_-]?key|secret|password|token|refresh_token|access_token)"
                    + "\\\"?\\s*[:=]\\s*\\\"?)([^\\s\\\",;}]+)")
    };

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = message;
        masked = PATTERNS[0].matcher(masked).replaceAll("$1$2 " + MASK);
        masked = PATTERNS[1].matcher(masked).replaceAll(MASK);
        masked = PATTERNS[2].matcher(masked).replaceAll(MASK);
        masked = PATTERNS[3].matcher(masked).replaceAll("$1" + MASK);
        return masked;
    }
}
