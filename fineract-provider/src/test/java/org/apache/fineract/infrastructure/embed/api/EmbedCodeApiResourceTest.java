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
package org.apache.fineract.infrastructure.embed.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import org.apache.fineract.infrastructure.embed.service.EmbedCode;
import org.apache.fineract.infrastructure.embed.service.EmbedCodeException;
import org.apache.fineract.infrastructure.embed.service.GoOsEmbedCodeClient;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedCodeApiResourceTest {

    private static final String KEY = "bWlmb3M6cGFzc3dvcmQ=";

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final GoOsEmbedCodeClient client = mock(GoOsEmbedCodeClient.class);
    private final EmbedCodeApiResource resource = new EmbedCodeApiResource(context, client);

    @BeforeEach
    void signedIn() {
        AppUser user = mock(AppUser.class);
        when(user.getUsername()).thenReturn("mifos");
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void mintsWithTheRequestsOwnBasicKeyAndReturnsOnlyTheCode() {
        when(client.mint(KEY)).thenReturn(new EmbedCode("one-time", 60));

        Response response = resource.mint("Basic " + KEY);

        assertEquals(200, response.getStatus());
        assertEquals("{\"code\":\"one-time\",\"expiresInSeconds\":60}", response.getEntity());
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        verify(client).mint(KEY);
    }

    @Test
    void refusesANonBasicAuthorizationWithoutCallingTheGateway() {
        assertEquals(400, resource.mint("Bearer abc").getStatus());
        assertEquals(400, resource.mint(null).getStatus());
        verify(client, never()).mint(any());
    }

    @Test
    void passesTheGatewaysStatusOnWithoutTheKey() {
        when(client.mint(KEY)).thenThrow(new EmbedCodeException(429, "too many"));

        Response response = resource.mint("basic " + KEY);

        assertEquals(429, response.getStatus());
        assertFalse(String.valueOf(response.getEntity()).contains(KEY));
    }

    @Test
    void basicKeyParsing() {
        assertEquals(KEY, EmbedCodeApiResource.basicKey("Basic " + KEY));
        assertEquals(KEY, EmbedCodeApiResource.basicKey("BASIC  " + KEY + " "));
        assertNull(EmbedCodeApiResource.basicKey("Basic "));
        assertNull(EmbedCodeApiResource.basicKey("Bearer " + KEY));
        assertNull(EmbedCodeApiResource.basicKey("Basicx" + KEY));
    }
}
