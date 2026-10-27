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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.sessiontoken.domain.StoredSessionToken;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenService;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenUsers;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

class SessionTokenAuthenticationFilterTest {

    private SessionTokenService service;
    private SessionTokenUsers users;
    private SessionTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        service = mock(SessionTokenService.class);
        users = mock(SessionTokenUsers.class);
        filter = new SessionTokenAuthenticationFilter(service, users);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leavesBasicAndForeignBearerHeadersAlone() throws Exception {
        for (String header : new String[] { null, "Basic bWlmb3M6cGFzc3dvcmQ=", "Bearer eyJhbGciOi.jwt" }) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fineract-provider/api/v1/clients");
            if (header != null) {
                request.addHeader("Authorization", header);
            }
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest(), "chain should continue for " + header);
        }
        verifyNoInteractions(service, users);
    }

    @Test
    void refusesOneOfOurTokensThatIsNotValid() throws Exception {
        when(service.authenticate("fgt_stale")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fineract-provider/api/v1/clients");
        request.addHeader("Authorization", "Bearer fgt_stale");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "an invalid token must not reach the API anonymous");
        assertEquals("Bearer error=\"invalid_token\"", response.getHeader("WWW-Authenticate"));
    }

    @Test
    void authenticatesAValidTokenAndSavesTheContextForTheStatelessChain() throws Exception {
        StoredSessionToken stored = new StoredSessionToken(1L, "chain", 7L, "mifos", 0L, 1L, 2L, null, null);
        AppUser user = mock(AppUser.class);
        when(user.getAuthorities()).thenReturn(List.of());
        when(service.authenticate("fgt_live")).thenReturn(Optional.of(stored));
        when(users.activeUser(stored)).thenReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fineract-provider/api/v1/clients");
        request.addHeader("Authorization", "Bearer fgt_live");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        var saved = new RequestAttributeSecurityContextRepository().loadDeferredContext(request).get();
        assertInstanceOf(SessionTokenAuthentication.class, saved.getAuthentication());
        assertEquals(user, saved.getAuthentication().getPrincipal());
    }
}
