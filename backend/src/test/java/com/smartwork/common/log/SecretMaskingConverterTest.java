package com.smartwork.common.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Secret masking in logs")
class SecretMaskingConverterTest {

    private final SecretMaskingConverter converter = new SecretMaskingConverter();

    @Test
    void masksAuthorizationHeaders() {
        String masked = convert("Outbound request with Authorization: Bearer abc123def456ghi789");

        assertThat(masked).doesNotContain("abc123def456ghi789");
        assertThat(masked).contains("REDACTED");
    }

    @Test
    void masksBareJwts() {
        String jwt = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMjM0NSJ9.c2lnbmF0dXJlLXZhbHVlLWhlcmU";
        String masked = convert("Verifying token " + jwt + " for request");

        assertThat(masked).doesNotContain(jwt);
        assertThat(masked).contains("REDACTED");
    }

    @Test
    void masksAtlassianTokens() {
        String masked = convert("Refreshed ATATTxFfGF0abcdefghijklmnopqrstuvwxyz012345 for site");

        assertThat(masked).doesNotContain("ATATTxFfGF0abcdefghijklmnopqrstuvwxyz012345");
    }

    @Test
    void masksAnythingNamingItselfASecret() {
        String masked = convert("config loaded: api_key=sk-live-9f8e7d6c, password=hunter2");

        assertThat(masked).doesNotContain("sk-live-9f8e7d6c");
        assertThat(masked).doesNotContain("hunter2");
    }

    @Test
    void leavesOrdinaryMessagesIntact() {
        String message = "Work item DEV-231 transitioned from IN_PROGRESS to CODE_REVIEW";

        assertThat(convert(message)).isEqualTo(message);
    }

    private String convert(String message) {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger("test");
        LoggingEvent event = new LoggingEvent(
                "com.smartwork.Test", logger, Level.INFO, message, null, new Object[0]);
        return converter.convert(event);
    }
}
