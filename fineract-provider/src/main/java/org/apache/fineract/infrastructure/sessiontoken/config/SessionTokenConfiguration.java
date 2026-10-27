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

import java.time.Clock;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.data.AuthenticatedUserData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.PlatformUserDetailsChecker;
import org.apache.fineract.infrastructure.security.service.TenantAwareJpaPlatformUserDetailsService;
import org.apache.fineract.infrastructure.sessiontoken.api.SessionTokensApiResource;
import org.apache.fineract.infrastructure.sessiontoken.filter.SessionTokenAuthenticationFilter;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenService;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenSettings;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenStore;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenUsers;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Session tokens for Fiter Go OS (Go OS ADR 0032, option B). Always registered, with no settings: Fineract gets no new
 * properties for this (product owner, 2026-10-20). Whether session tokens are used is the gateway's choice
 * ({@code fineract_session.credential_mode}); until it mints one, nothing here is called, and a request without an
 * {@code fgt_} token passes the filter untouched, so Basic sign-in works as before.
 *
 * <p>
 * {@code SecurityConfig} places the filter on the API chain after {@code TenantAwareBasicAuthenticationFilter}. Its
 * servlet registration is disabled so it runs only there, never as a container-wide filter outside the tenant context.
 */
@Configuration
public class SessionTokenConfiguration {

    @Bean
    public SessionTokenSettings sessionTokenSettings() {
        return SessionTokenSettings.FIXED;
    }

    @Bean
    public SessionTokenStore sessionTokenStore(JdbcTemplate jdbcTemplate) {
        return new SessionTokenStore(jdbcTemplate);
    }

    @Bean
    public SessionTokenUsers sessionTokenUsers(TenantAwareJpaPlatformUserDetailsService userDetailsService,
            PlatformUserDetailsChecker userDetailsChecker) {
        return new SessionTokenUsers(userDetailsService, userDetailsChecker);
    }

    @Bean
    public SessionTokenService sessionTokenService(SessionTokenStore store, SessionTokenUsers users, SessionTokenSettings settings) {
        return new SessionTokenService(store, users, settings, Clock.systemUTC());
    }

    @Bean
    public SessionTokenAuthenticationFilter sessionTokenAuthenticationFilter(SessionTokenService service, SessionTokenUsers users) {
        return new SessionTokenAuthenticationFilter(service, users);
    }

    @Bean
    public FilterRegistrationBean<SessionTokenAuthenticationFilter> sessionTokenAuthenticationFilterRegistration(
            SessionTokenAuthenticationFilter filter) {
        FilterRegistrationBean<SessionTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SessionTokensApiResource sessionTokensApiResource(PlatformSecurityContext context, SessionTokenService service,
            ToApiJsonSerializer<AuthenticatedUserData> userSerializer) {
        return new SessionTokensApiResource(context, service, userSerializer);
    }
}
