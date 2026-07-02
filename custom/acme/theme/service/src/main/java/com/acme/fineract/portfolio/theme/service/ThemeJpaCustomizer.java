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
package com.acme.fineract.portfolio.theme.service;

import java.util.Set;
import org.apache.fineract.infrastructure.core.config.jpa.EntityManagerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Registers the theme module's entity package with Fineract's shared {@code jpa-pu} persistence unit. Spring Boot's
 * {@code @EntityScan} has no effect here because {@code JPAConfig} overrides the default EntityManagerFactory and only
 * scans the {@code org.apache.fineract} package plus whatever this customizer contributes.
 */
@Component
public class ThemeJpaCustomizer implements EntityManagerFactoryCustomizer {

    @Override
    public Set<String> additionalPackagesToScan() {
        // .domain: JPA entities (UserThemePreference, ThemeSkin, ...) and enum converters
        // .serialization: JsonNodeAttributeConverter — EclipseLink resolves @Convert classes
        // against the persistence unit's scanned packages, so referenced
        // converters must live under a scanned package or load will fail
        // with "converter class ... was not found".
        return Set.of("com.acme.fineract.portfolio.theme.domain", "com.acme.fineract.portfolio.theme.serialization");
    }
}
