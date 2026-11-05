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
package org.apache.fineract.infrastructure.fitersession.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {

    /** A clock the test moves. */
    static final class MutableClock extends Clock {

        Instant now = Instant.parse("2026-11-05T09:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final InMemorySessionTokenStore store = new InMemorySessionTokenStore();
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppUser user = mock(AppUser.class);
    private final MutableClock clock = new MutableClock();
    private SessionTokenService service;

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(7L);
        when(user.getPassword()).thenReturn("$2a$10$hash-one");
        when(user.isEnabled()).thenReturn(true);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isAccountNonExpired()).thenReturn(true);
        when(user.isCredentialsNonExpired()).thenReturn(true);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        SessionTokenProperties properties = new SessionTokenProperties(Duration.ofMinutes(15), Duration.ofHours(12),
                Duration.ofSeconds(30));
        service = new SessionTokenService(store, users, properties, clock);
    }

    @Test
    void mintIssuesAFifteenMinuteTokenInsideTheAskedMaximum() {
        IssuedSessionToken token = service.mint(user, 4 * 3600);
        assertTrue(token.token().startsWith("fst_"));
        assertEquals(900, token.expiresInSeconds());
        assertEquals(4 * 3600, token.chainExpiresInSeconds());
        assertEquals(SessionTokens.hash(token.token()), store.rows.get(0).tokenHash());
    }

    @Test
    void mintCapsTheChainAtTheConfiguredCeiling() {
        assertEquals(12 * 3600, service.mint(user, 48 * 3600).chainExpiresInSeconds());
    }

    @Test
    void mintCapsTheTokenAtAShortChain() {
        IssuedSessionToken token = service.mint(user, 600);
        assertEquals(600, token.expiresInSeconds());
    }

    @Test
    void mintRefusesANonPositiveMaximum() {
        SessionTokenException e = assertThrows(SessionTokenException.class, () -> service.mint(user, 0));
        assertEquals(400, e.getStatus());
    }

    @Test
    void aFreshTokenReadsUntilItsLifetimeEnds() {
        String token = service.mint(user, 3600).token();
        assertSame(user, service.authenticateForRead(token).orElseThrow());
        clock.advance(Duration.ofMinutes(15));
        assertTrue(service.authenticateForRead(token).isEmpty());
    }

    @Test
    void anUnknownTokenDoesNotRead() {
        assertTrue(service.authenticateForRead("fst_unknown").isEmpty());
    }

    @Test
    void renewIssuesTheNextTokenAndTheOldOneReadsOnlyForTheGrace() {
        String first = service.mint(user, 3600).token();
        clock.advance(Duration.ofMinutes(5));
        RenewedSessionToken renewed = service.renew(first);
        String second = renewed.token().token();
        assertNotEquals(first, second);
        assertSame(user, renewed.user());
        assertTrue(service.authenticateForRead(second).isPresent());
        clock.advance(Duration.ofSeconds(29));
        assertTrue(service.authenticateForRead(first).isPresent());
        clock.advance(Duration.ofSeconds(2));
        assertTrue(service.authenticateForRead(first).isEmpty());
        assertTrue(service.authenticateForRead(second).isPresent());
    }

    @Test
    void renewingARenewedTokenEndsTheWholeChain() {
        String first = service.mint(user, 3600).token();
        String second = service.renew(first).token().token();
        SessionTokenException e = assertThrows(SessionTokenException.class, () -> service.renew(first));
        assertEquals(401, e.getStatus());
        assertTrue(service.authenticateForRead(second).isEmpty());
        assertThrows(SessionTokenException.class, () -> service.renew(second));
    }

    @Test
    void aLapsedTokenCanStillBeRenewedInsideTheChain() {
        String first = service.mint(user, 3600).token();
        clock.advance(Duration.ofMinutes(20));
        assertTrue(service.authenticateForRead(first).isEmpty());
        String second = service.renew(first).token().token();
        assertTrue(service.authenticateForRead(second).isPresent());
    }

    @Test
    void renewEndsAtTheChainMaximum() {
        String first = service.mint(user, 1200).token();
        clock.advance(Duration.ofMinutes(10));
        IssuedSessionToken second = service.renew(first).token();
        assertEquals(600, second.expiresInSeconds());
        clock.advance(Duration.ofMinutes(10));
        assertEquals(401, assertThrows(SessionTokenException.class, () -> service.renew(second.token())).getStatus());
    }

    @Test
    void aPasswordChangeEndsTheChain() {
        String token = service.mint(user, 3600).token();
        when(user.getPassword()).thenReturn("$2a$10$hash-two");
        assertTrue(service.authenticateForRead(token).isEmpty());
        assertEquals(401, assertThrows(SessionTokenException.class, () -> service.renew(token)).getStatus());
        when(user.getPassword()).thenReturn("$2a$10$hash-one");
        assertTrue(service.authenticateForRead(token).isEmpty(), "the chain stays revoked");
    }

    @Test
    void aDisabledOrDeletedUserDoesNotRead() {
        String token = service.mint(user, 3600).token();
        when(user.isEnabled()).thenReturn(false);
        assertTrue(service.authenticateForRead(token).isEmpty());
        when(user.isEnabled()).thenReturn(true);
        when(user.isDeleted()).thenReturn(true);
        assertTrue(service.authenticateForRead(token).isEmpty());
    }

    @Test
    void revokeEndsEveryTokenOfTheChain() {
        String first = service.mint(user, 3600).token();
        String second = service.renew(first).token().token();
        service.revoke(second);
        assertTrue(service.authenticateForRead(first).isEmpty());
        assertTrue(service.authenticateForRead(second).isEmpty());
        service.revoke("fst_unknown");
    }

    @Test
    void mintPurgesChainsPastTheirMaximum() {
        service.mint(user, 60);
        clock.advance(Duration.ofMinutes(2));
        service.mint(user, 60);
        assertEquals(1, store.rows.size());
    }
}
