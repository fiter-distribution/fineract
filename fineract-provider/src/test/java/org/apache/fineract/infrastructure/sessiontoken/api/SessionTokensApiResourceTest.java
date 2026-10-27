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
package org.apache.fineract.infrastructure.sessiontoken.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.data.AuthenticatedUserData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.sessiontoken.filter.SessionTokenAuthentication;
import org.apache.fineract.infrastructure.sessiontoken.service.IssuedSessionToken;
import org.apache.fineract.infrastructure.sessiontoken.service.RenewalResult;
import org.apache.fineract.infrastructure.sessiontoken.service.RenewedSessionToken;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

/** The session-token endpoints against the Go OS ADR 0032 fork contract. */
class SessionTokensApiResourceTest {

    private static final IssuedSessionToken ISSUED = new IssuedSessionToken("fgt_new", 1_000L, 2_000L);

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final SessionTokenService service = mock(SessionTokenService.class);
    @SuppressWarnings("unchecked")
    private final ToApiJsonSerializer<AuthenticatedUserData> serializer = mock(ToApiJsonSerializer.class);
    private final SessionTokensApiResource resource = new SessionTokensApiResource(context, service, serializer);
    private AppUser user;

    @BeforeEach
    void signedIn() {
        user = mock(AppUser.class);
        Office office = mock(Office.class);
        when(office.getId()).thenReturn(1L);
        when(office.getName()).thenReturn("Head Office");
        when(user.getId()).thenReturn(7L);
        when(user.getUsername()).thenReturn("mifos");
        when(user.getOffice()).thenReturn(office);
        when(user.getRoles()).thenReturn(Set.of());
        when(user.getAuthorities()).thenReturn(List.of());
        when(context.authenticatedUser()).thenReturn(user);
        when(serializer.serialize(any(AuthenticatedUserData.class))).thenReturn("{\"username\":\"mifos\"}");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mintReturnsTheTokenAndTheUserWithoutTheKey() {
        when(service.mint(user, Duration.ofSeconds(14_400))).thenReturn(ISSUED);

        Response response = resource.mint("{\"maxLifetimeSeconds\":14400}");

        assertEquals(200, response.getStatus());
        String body = String.valueOf(response.getEntity());
        assertTrue(body.contains("\"token\":\"fgt_new\""), body);
        assertTrue(body.contains("\"expiresAtEpochMillis\":1000"), body);
        assertTrue(body.contains("\"chainExpiresAtEpochMillis\":2000"), body);
        assertTrue(body.contains("\"user\":{\"username\":\"mifos\"}"), body);
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        ArgumentCaptor<AuthenticatedUserData> data = ArgumentCaptor.forClass(AuthenticatedUserData.class);
        verify(serializer).serialize(data.capture());
        assertNull(data.getValue().getBase64EncodedAuthenticationKey());
        assertEquals(7L, data.getValue().getUserId());
        assertEquals("Head Office", data.getValue().getOfficeName());
    }

    @Test
    void mintRefusesATokenSignIn() {
        SecurityContextHolder.getContext().setAuthentication(new SessionTokenAuthentication(user, List.of()));

        Response response = resource.mint("{}");

        assertEquals(403, response.getStatus());
        assertEquals("{\"error\":\"password_sign_in_required\"}", response.getEntity());
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        verify(service, never()).mint(any(), any());
    }

    @Test
    void mintRefusesAMaximumThatIsNotAWholeNumber() {
        for (String body : new String[] { "{\"maxLifetimeSeconds\":1.5}", "{\"maxLifetimeSeconds\":\"60\"}",
                "{\"maxLifetimeSeconds\":true}", "[]", "not json" }) {
            Response response = resource.mint(body);
            assertEquals(400, response.getStatus(), body);
            assertEquals("{\"error\":\"invalid_request\"}", response.getEntity(), body);
        }
        verify(service, never()).mint(any(), any());
    }

    @Test
    void renewReturnsTheSuccessorAndTheUserAsNow() {
        when(service.renew("fgt_old")).thenReturn(RenewalResult.renewed(new RenewedSessionToken(ISSUED, user)));

        Response response = resource.renew("{\"token\":\"fgt_old\"}");

        assertEquals(200, response.getStatus());
        String body = String.valueOf(response.getEntity());
        assertTrue(body.contains("\"token\":\"fgt_new\"") && body.contains("\"user\":{"), body);
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
    }

    @Test
    void renewTellsAnEndedSessionFromAPersonWhoMayNotSignIn() {
        when(service.renew("fgt_gone")).thenReturn(RenewalResult.failed(RenewalResult.Failure.INVALID_TOKEN));
        when(service.renew("fgt_disabled")).thenReturn(RenewalResult.failed(RenewalResult.Failure.USER_NOT_ACTIVE));

        Response gone = resource.renew("{\"token\":\"fgt_gone\"}");
        Response disabled = resource.renew("{\"token\":\"fgt_disabled\"}");

        assertEquals(401, gone.getStatus());
        assertEquals("{\"error\":\"invalid_token\"}", gone.getEntity());
        assertEquals(403, disabled.getStatus());
        assertEquals("{\"error\":\"user_not_active\"}", disabled.getEntity());
        assertEquals("no-store", disabled.getHeaderString("Cache-Control"));
        assertFalse(String.valueOf(disabled.getEntity()).contains("fgt_"));
    }

    @Test
    void renewWithoutATokenIsAnInvalidRequest() {
        for (String body : new String[] { "{}", "{\"token\":7}", "{\"token\":{}}", "", "nope" }) {
            Response response = resource.renew(body);
            assertEquals(400, response.getStatus(), body);
            assertEquals("{\"error\":\"invalid_request\"}", response.getEntity(), body);
        }
        verify(service, never()).renew(anyString());
    }

    @Test
    void revokeAlwaysAnswers204() {
        Response known = resource.revoke("{\"token\":\"fgt_known\"}");
        Response empty = resource.revoke("");

        assertEquals(204, known.getStatus());
        assertEquals(204, empty.getStatus());
        assertEquals("no-store", known.getHeaderString("Cache-Control"));
        verify(service).revoke("fgt_known");
    }
}
