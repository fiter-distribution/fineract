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
package org.apache.fineract.infrastructure.sessiontoken.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenCodec;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenService;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenUsers;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code Authorization: Bearer fgt_...} on the API chain. It runs inside
 * {@code TenantAwareBasicAuthenticationFilter}, so the tenant is already chosen when the token is looked up. Any other
 * header passes through untouched; a token of ours that is not valid is refused with 401 rather than let through
 * anonymous.
 */
@RequiredArgsConstructor
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final SessionTokenService service;
    private final SessionTokenUsers users;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        if (!SessionTokenCodec.looksLikeToken(token)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<AppUser> user = service.authenticate(token).flatMap(users::activeUser);
        if (user.isEmpty()) {
            contextHolder.clearContext();
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(new SessionTokenAuthentication(user.get(), user.get().getAuthorities()));
        contextHolder.setContext(context);
        // The API chain is stateless; SecurityContextHolderFilter reloads the context from the request attribute.
        contextRepository.saveContext(context, request, response);
        chain.doFilter(request, response);
    }
}
