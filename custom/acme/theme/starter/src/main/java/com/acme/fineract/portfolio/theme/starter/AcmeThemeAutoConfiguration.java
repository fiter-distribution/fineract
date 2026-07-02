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
package com.acme.fineract.portfolio.theme.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auto-configuration for the ACME theme module.
 *
 * Unlike sibling acme modules (e.g. note, loan) which are opt-in demos enabled only when
 * {@code acme.note.enabled=true}, the theme module is a user-facing feature consumed by the FE and is therefore enabled
 * by default via {@code matchIfMissing = true}. Operators can still disable it explicitly with
 * {@code acme.theme.enabled=false}.
 *
 * Note: Spring Boot's {@code @EntityScan} is intentionally NOT used here; Fineract's {@code JPAConfig} overrides the
 * default {@code EntityManagerFactory} and only honors
 * {@link org.apache.fineract.infrastructure.core.config.jpa.EntityManagerFactoryCustomizer} for additional packages.
 * See {@code ThemeJpaCustomizer}.
 */
@AutoConfiguration
@ComponentScan("com.acme.fineract.portfolio.theme")
@EnableJpaRepositories("com.acme.fineract.portfolio.theme.domain")
@ConditionalOnProperty(name = "acme.theme.enabled", havingValue = "true", matchIfMissing = true)
public class AcmeThemeAutoConfiguration {}
