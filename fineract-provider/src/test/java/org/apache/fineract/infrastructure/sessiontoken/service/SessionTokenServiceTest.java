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
package org.apache.fineract.infrastructure.sessiontoken.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.apache.fineract.infrastructure.sessiontoken.domain.StoredSessionToken;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionTokenServiceTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long MINUTE = 60_000L;

    private SessionTokenStore store;
    private SessionTokenUsers users;
    private SessionTokenService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        store = mock(SessionTokenStore.class);
        users = mock(SessionTokenUsers.class);
        user = mock(AppUser.class);
        when(user.getId()).thenReturn(7L);
        when(user.getUsername()).thenReturn("mifos");
        service = new SessionTokenService(store, users,
                new SessionTokenSettings(Duration.ofMinutes(15), Duration.ofHours(8), Duration.ofSeconds(30)),
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
    }

    @Test
    void mintStoresOnlyTheHashAndUsesTheSmallerMaximum() {
        IssuedSessionToken issued = service.mint(user, Duration.ofHours(4));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(store).insert(hash.capture(), anyString(), eq(7L), eq("mifos"), eq(NOW), eq(NOW + 15 * MINUTE), eq(NOW + 240 * MINUTE));
        assertTrue(issued.token().startsWith(SessionTokenCodec.PREFIX));
        assertNotEquals(issued.token(), hash.getValue());
        assertEquals(SessionTokenCodec.hash(issued.token()), hash.getValue());
        assertEquals(NOW + 240 * MINUTE, issued.chainExpiresAt());
    }

    @Test
    void mintCapsARequestedMaximumAboveTheServerCeiling() {
        IssuedSessionToken issued = service.mint(user, Duration.ofHours(48));

        assertEquals(NOW + 8 * 60 * MINUTE, issued.chainExpiresAt());
    }

    @Test
    void renewIssuesASuccessorInTheSameChainAndReplacesThePresentedToken() {
        StoredSessionToken stored = stored(null, null, NOW - 20 * MINUTE, NOW + 5 * MINUTE);
        when(store.findByHash(SessionTokenCodec.hash("fgt_old"))).thenReturn(Optional.of(stored));
        when(users.activeUser(stored)).thenReturn(Optional.of(user));
        when(store.markReplaced(1L, NOW)).thenReturn(true);

        RenewalResult result = service.renew("fgt_old");

        assertTrue(result.successor().isPresent());
        assertNull(result.failure());
        assertSame(user, result.successor().get().user());
        // Its own window is capped by what is left of the chain.
        assertEquals(NOW + 5 * MINUTE, result.successor().get().issued().expiresAt());
        verify(store).insert(anyString(), eq("chain"), eq(7L), eq("mifos"), eq(NOW), eq(NOW + 5 * MINUTE), eq(NOW + 5 * MINUTE));
        verify(store, never()).revokeChain(anyString(), anyLong());
    }

    @Test
    void renewOfARenewedTokenEndsTheChain() {
        StoredSessionToken stored = stored(NOW - MINUTE, null, NOW - 20 * MINUTE, NOW + 200 * MINUTE);
        when(store.findByHash(SessionTokenCodec.hash("fgt_old"))).thenReturn(Optional.of(stored));

        assertEquals(RenewalResult.Failure.INVALID_TOKEN, service.renew("fgt_old").failure());
        verify(store).revokeChain("chain", NOW);
        verify(store, never()).insert(anyString(), anyString(), anyLong(), anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void renewThatLosesARaceEndsTheChain() {
        StoredSessionToken stored = stored(null, null, NOW - MINUTE, NOW + 200 * MINUTE);
        when(store.findByHash(SessionTokenCodec.hash("fgt_old"))).thenReturn(Optional.of(stored));
        when(users.activeUser(stored)).thenReturn(Optional.of(user));
        when(store.markReplaced(1L, NOW)).thenReturn(false);

        assertEquals(RenewalResult.Failure.INVALID_TOKEN, service.renew("fgt_old").failure());
        verify(store).revokeChain("chain", NOW);
    }

    @Test
    void renewForADisabledUserEndsTheChain() {
        StoredSessionToken stored = stored(null, null, NOW + MINUTE, NOW + 200 * MINUTE);
        when(store.findByHash(SessionTokenCodec.hash("fgt_old"))).thenReturn(Optional.of(stored));
        when(users.activeUser(stored)).thenReturn(Optional.empty());

        RenewalResult result = service.renew("fgt_old");
        // Not "invalid token": the caller answers 403 so the gateway ends every session of the person.
        assertEquals(RenewalResult.Failure.USER_NOT_ACTIVE, result.failure());
        assertTrue(result.successor().isEmpty());
        verify(store).revokeChain("chain", NOW);
        verify(store, never()).markReplaced(anyLong(), anyLong());
    }

    @Test
    void renewOfAnUnknownTokenIsAnInvalidToken() {
        when(store.findByHash(anyString())).thenReturn(Optional.empty());

        assertEquals(RenewalResult.Failure.INVALID_TOKEN, service.renew("fgt_unknown").failure());
        assertEquals(RenewalResult.Failure.INVALID_TOKEN, service.renew("not-a-token").failure());
        verify(store, never()).revokeChain(anyString(), anyLong());
    }

    @Test
    void renewPastTheChainMaximumIsRefusedWithoutRevoking() {
        StoredSessionToken stored = stored(null, null, NOW - MINUTE, NOW);
        when(store.findByHash(SessionTokenCodec.hash("fgt_old"))).thenReturn(Optional.of(stored));

        assertEquals(RenewalResult.Failure.INVALID_TOKEN, service.renew("fgt_old").failure());
        verify(store, never()).revokeChain(anyString(), anyLong());
        verify(users, never()).activeUser(any());
    }

    @Test
    void aRenewedTokenStillAuthenticatesReadsForTheGrace() {
        when(store.findByHash(SessionTokenCodec.hash("fgt_just_renewed")))
                .thenReturn(Optional.of(stored(NOW - 29_000L, null, NOW + MINUTE, NOW + 9 * MINUTE)));
        when(store.findByHash(SessionTokenCodec.hash("fgt_grace_over")))
                .thenReturn(Optional.of(stored(NOW - 30_000L, null, NOW + MINUTE, NOW + 9 * MINUTE)));

        assertTrue(service.authenticate("fgt_just_renewed").isPresent());
        assertTrue(service.authenticate("fgt_grace_over").isEmpty());
    }

    @Test
    void authenticateAcceptsOnlyAnActiveToken() {
        when(store.findByHash(SessionTokenCodec.hash("fgt_live"))).thenReturn(Optional.of(stored(null, null, NOW + MINUTE, NOW + 9 * MINUTE)));
        when(store.findByHash(SessionTokenCodec.hash("fgt_lapsed"))).thenReturn(Optional.of(stored(null, null, NOW, NOW + 9 * MINUTE)));
        when(store.findByHash(SessionTokenCodec.hash("fgt_renewed")))
                .thenReturn(Optional.of(stored(NOW - MINUTE, null, NOW + MINUTE, NOW + 9 * MINUTE)));

        assertTrue(service.authenticate("fgt_live").isPresent());
        assertTrue(service.authenticate("fgt_lapsed").isEmpty());
        assertTrue(service.authenticate("fgt_renewed").isEmpty());
        assertTrue(service.authenticate("Basic abc").isEmpty());
    }

    @Test
    void revokeEndsTheChainOfAKnownTokenOnly() {
        when(store.findByHash(SessionTokenCodec.hash("fgt_known"))).thenReturn(Optional.of(stored(null, null, NOW, NOW)));
        when(store.findByHash(SessionTokenCodec.hash("fgt_unknown"))).thenReturn(Optional.empty());

        assertTrue(service.revoke("fgt_known"));
        assertFalse(service.revoke("fgt_unknown"));
        verify(store).revokeChain(eq("chain"), any(Long.class));
    }

    private static StoredSessionToken stored(Long replacedAt, Long revokedAt, long expiresAt, long chainExpiresAt) {
        return new StoredSessionToken(1L, "chain", 7L, "mifos", NOW - 30 * MINUTE, expiresAt, chainExpiresAt, replacedAt, revokedAt);
    }
}
