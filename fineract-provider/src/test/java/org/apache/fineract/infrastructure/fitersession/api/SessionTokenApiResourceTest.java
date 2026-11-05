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
package org.apache.fineract.infrastructure.fitersession.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.fitersession.service.IssuedSessionToken;
import org.apache.fineract.infrastructure.fitersession.service.RenewedSessionToken;
import org.apache.fineract.infrastructure.fitersession.service.SessionTokenException;
import org.apache.fineract.infrastructure.fitersession.service.SessionTokenService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SessionTokenApiResourceTest {

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final SessionTokenService service = mock(SessionTokenService.class);
    private final SessionTokenApiResource resource = new SessionTokenApiResource(context, service);
    private final AppUser user = mock(AppUser.class);

    @Test
    void mintReturnsTheTokenAndItsLifetimes() {
        when(context.authenticatedUser()).thenReturn(user);
        when(service.mint(user, 14400)).thenReturn(new IssuedSessionToken("fst_new", 900, 14400));
        Response response = resource.mint("Basic bWlmb3M6cGFzc3dvcmQ=", "{\"maxSeconds\":14400}");
        assertEquals(200, response.getStatus());
        String body = (String) response.getEntity();
        assertTrue(body.contains("\"token\":\"fst_new\""));
        assertTrue(body.contains("\"expiresInSeconds\":900"));
        assertTrue(body.contains("\"chainExpiresInSeconds\":14400"));
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
    }

    @Test
    void aSessionTokenCannotMint() {
        Response response = resource.mint("Bearer fst_abc", "{\"maxSeconds\":60}");
        assertEquals(400, response.getStatus());
        verifyNoInteractions(service);
    }

    @Test
    void mintNeedsMaxSeconds() {
        when(context.authenticatedUser()).thenReturn(user);
        assertEquals(400, resource.mint("Basic x", "{}").getStatus());
        assertEquals(400, resource.mint("Basic x", "not json").getStatus());
        assertEquals(400, resource.mint("Basic x", null).getStatus());
    }

    @Test
    void mintPassesTheServiceRefusalOn() {
        when(context.authenticatedUser()).thenReturn(user);
        when(service.mint(any(), anyLong())).thenThrow(new SessionTokenException(400, "maxSeconds must be positive"));
        assertEquals(400, resource.mint("Basic x", "{\"maxSeconds\":0}").getStatus());
    }

    @Test
    void renewReturnsTheNextTokenAndTheUserWithoutAKey() {
        Office office = mock(Office.class);
        when(office.getId()).thenReturn(1L);
        when(office.getName()).thenReturn("Head Office");
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(2L);
        when(role.getName()).thenReturn("Loan Officer");
        when(user.getId()).thenReturn(7L);
        when(user.getUsername()).thenReturn("officer");
        when(user.getOffice()).thenReturn(office);
        when(user.getRoles()).thenReturn(Set.of(role));
        when(user.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("READ_LOAN")));
        when(service.renew("fst_old")).thenReturn(new RenewedSessionToken(new IssuedSessionToken("fst_next", 900, 3000), user));

        Response response = resource.renew("Bearer fst_old");
        assertEquals(200, response.getStatus());
        String body = (String) response.getEntity();
        assertTrue(body.contains("\"token\":\"fst_next\""));
        assertTrue(body.contains("\"authenticated\":true"));
        assertTrue(body.contains("\"username\":\"officer\""));
        assertTrue(body.contains("\"officeName\":\"Head Office\""));
        assertTrue(body.contains("\"name\":\"Loan Officer\""));
        assertTrue(body.contains("\"permissions\":[\"READ_LOAN\"]"));
        assertFalse(body.contains("base64EncodedAuthenticationKey"));
    }

    @Test
    void renewWithoutASessionTokenIsUnauthorized() {
        assertEquals(401, resource.renew("Basic x").getStatus());
        assertEquals(401, resource.renew(null).getStatus());
        verifyNoInteractions(service);
    }

    @Test
    void renewPassesTheServiceRefusalOn() {
        when(service.renew("fst_old")).thenThrow(new SessionTokenException(401, "session token not valid"));
        Response response = resource.renew("Bearer fst_old");
        assertEquals(401, response.getStatus());
        assertFalse(((String) response.getEntity()).contains("fst_old"));
    }

    @Test
    void revokeIsAlwaysNoContent() {
        assertEquals(204, resource.revoke("Bearer fst_abc").getStatus());
        verify(service).revoke("fst_abc");
        assertEquals(204, resource.revoke(null).getStatus());
    }
}
