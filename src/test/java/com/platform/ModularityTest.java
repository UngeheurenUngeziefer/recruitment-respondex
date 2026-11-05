package com.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies module boundaries: a module must not import another module's internal classes.
 * Modules: shared, audit, budget, and study (see architecture section 3.1).
 */
class ModularityTest {

    @Test
    void moduleBoundariesAreRespected() {
        ApplicationModules.of(PlatformApplication.class).verify();
    }
}
