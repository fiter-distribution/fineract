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
package org.apache.fineract.infrastructure.fitersession.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.fitersession.service.SessionTokenService;
import org.apache.fineract.infrastructure.fitersession.service.SessionTokens;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request that presents a Fiter session token ({@code Authorization: Bearer fst_...}). It runs after
 * the tenant filter, which has already bound the tenant, and touches no other request. A session token authenticates
 * reads only: any method but GET and HEAD is refused. Renew and revoke are left to their resource, which checks the
 * token itself because a lapsed token may still be renewed.
 */
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {

    static final String RENEW_SUFFIX = "/fiter/session-tokens/renew";
    static final String CURRENT_SUFFIX = "/fiter/session-tokens/current";

    private final SessionTokenService service;
    private final SecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();
    private final ObjectMapper json = new ObjectMapper();

    public SessionTokenAuthenticationFilter(SessionTokenService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SessionTokens.bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION)) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && (path.endsWith(RENEW_SUFFIX) || path.endsWith(CURRENT_SUFFIX))) {
            chain.doFilter(request, response);
            return;
        }
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            refuse(response, HttpServletResponse.SC_FORBIDDEN, "session tokens are read-only");
            return;
        }
        Optional<AppUser> user = service.authenticateForRead(SessionTokens.bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION)));
        if (user.isEmpty()) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            refuse(response, HttpServletResponse.SC_UNAUTHORIZED, "session token not valid");
            return;
        }
        AppUser principal = user.get();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        chain.doFilter(request, response);
    }

    private void refuse(HttpServletResponse response, int status, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(json.writeValueAsString(Map.of("developerMessage", message)));
    }
}
