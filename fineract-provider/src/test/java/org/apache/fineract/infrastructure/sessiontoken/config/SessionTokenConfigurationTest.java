/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.sessiontoken.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenSettings;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotations;

/**
 * No settings on Fineract (Go OS ADR 0032 fork contract, product owner 2026-10-20): the module is always registered,
 * and its lifetimes are fixed in code.
 */
class SessionTokenConfigurationTest {

    @Test
    void lifetimesAreFixedInCode() {
        SessionTokenSettings settings = new SessionTokenConfiguration().sessionTokenSettings();

        assertSame(SessionTokenSettings.FIXED, settings);
        assertEquals(Duration.ofMinutes(15), settings.ttl());
        assertEquals(Duration.ofHours(4), settings.maxLifetime());
        assertEquals(Duration.ofSeconds(30), settings.readGrace());
    }

    @Test
    void isAlwaysRegistered() {
        assertFalse(
                MergedAnnotations.from(SessionTokenConfiguration.class).isPresent(org.springframework.context.annotation.Conditional.class),
                "the configuration must not depend on a property");
    }
}
