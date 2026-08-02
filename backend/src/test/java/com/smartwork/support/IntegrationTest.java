package com.smartwork.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full-context integration test against a real PostgreSQL in Testcontainers.
 *
 * <p>Real Postgres rather than H2: the schema relies on partial unique indexes, CHECK
 * constraints, {@code pg_trgm} and trigger functions, none of which H2 reproduces. A test
 * suite that passes on a database you do not deploy is worth very little.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
public @interface IntegrationTest {
}
