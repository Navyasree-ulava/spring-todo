package com.todoapp;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared setup for integration tests: a real PostgreSQL in a throwaway
 * container.
 * <p>
 * Deliberately not H2. An in-memory database speaks a different SQL dialect and
 * skips schema creation, so it would happily pass tests that fail against the
 * Postgres the app actually uses - and it would not exercise the Flyway
 * migrations at all. Here the engine matches production; only the lifecycle
 * differs.
 * <p>
 * <b>Why this is started manually rather than with {@code @Container}:</b> the
 * Testcontainers extension stops {@code @Container} fields when each test class
 * finishes. Both test classes produce an identical Spring context, so the second
 * one reuses the cached context - which still points at the port of the container
 * that has just been shut down. The result is a connection-refused timeout
 * rather than an honest test failure.
 * <p>
 * Starting one container in a static initialiser gives every test class the same
 * instance and the same port, so the context stays valid. Testcontainers' Ryuk
 * still reaps the container when the JVM exits.
 * <p>
 * {@code @ServiceConnection} passes the container's details to Spring
 * automatically, so no URLs or credentials are duplicated in test config.
 */
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("todoapp_test")
			.withUsername("test")
			.withPassword("test");

	static {
		POSTGRES.start();
	}

}
