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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.fitersession.service.SessionTokenService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionTokenAuthenticationFilterTest {

    private final SessionTokenService service = mock(SessionTokenService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final SessionTokenAuthenticationFilter filter = new SessionTokenAuthenticationFilter(service);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String method, String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    void aRequestWithoutASessionTokenPassesUntouched() throws Exception {
        MockHttpServletRequest request = request("POST", "/fineract-provider/api/v1/loans", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(service);
    }

    @Test
    void aBasicRequestPassesUntouched() throws Exception {
        MockHttpServletRequest request = request("POST", "/fineract-provider/api/v1/loans", "Basic bWlmb3M6cGFzc3dvcmQ=");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(service);
    }

    @Test
    void aSessionTokenCannotWrite() throws Exception {
        MockHttpServletRequest request = request("POST", "/fineract-provider/api/v1/loans", "Bearer fst_abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("read-only"));
        verify(chain, never()).doFilter(request, response);
        verifyNoInteractions(service);
    }

    @Test
    void aValidTokenAuthenticatesARead() throws Exception {
        AppUser user = mock(AppUser.class);
        when(user.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("READ_LOAN")));
        when(service.authenticateForRead("fst_abc")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = request("GET", "/fineract-provider/api/v1/loans/1", "Bearer fst_abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertSame(user, auth.getPrincipal());
            assertTrue(auth.isAuthenticated());
            assertEquals("READ_LOAN", auth.getAuthorities().iterator().next().getAuthority());
        });
        assertEquals(200, response.getStatus());
    }

    @Test
    void anInvalidTokenIsRefused() throws Exception {
        when(service.authenticateForRead(anyString())).thenReturn(Optional.empty());
        MockHttpServletRequest request = request("GET", "/fineract-provider/api/v1/loans/1", "Bearer fst_abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(!response.getContentAsString().contains("fst_abc"));
        verify(chain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void renewAndRevokeAreLeftToTheirResource() throws Exception {
        for (String[] call : new String[][] { { "POST", "/fineract-provider/api/v1/fiter/session-tokens/renew" },
                { "DELETE", "/fineract-provider/api/v1/fiter/session-tokens/current" } }) {
            MockHttpServletRequest request = request(call[0], call[1], "Bearer fst_abc");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
        verifyNoInteractions(service);
    }
}
