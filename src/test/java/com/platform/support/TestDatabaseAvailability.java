package com.platform.support;

import org.testcontainers.DockerClientFactory;

/**
 * Decides whether database integration tests can run. Kept in its own class (not in
 * AbstractPostgresIT) so that evaluating the condition does not start the database.
 */
public final class TestDatabaseAvailability {
    private TestDatabaseAvailability() {
    }

    /**
     * True if TEST_DB_URL points to a database, or if Docker is available for Testcontainers.
     */
    public static boolean databaseAvailable() {
        String externalUrl = System.getenv("TEST_DB_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            return true;
        }
        return DockerClientFactory.instance().isDockerAvailable();
    }
}