package com.rentalroom.management;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Loads the full Spring context against H2 (ddl-auto=create) instead of the real MySQL schema.
 * This does not validate the Flyway migration, but it does catch bugs no compiler can see:
 * invalid Spring Data derived query method names, missing beans, bad security config wiring.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextSmokeTest {

    @Test
    void contextLoads() {
    }
}
