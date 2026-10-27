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
package org.apache.fineract.infrastructure.sessiontoken.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Go OS ADR 0032, decision 6. */
class RenewalPolicyTest {

    private static final long MINUTE = 60_000L;
    private static final long ISSUED = 1_000_000L;
    private static final long EXPIRES = ISSUED + 15 * MINUTE;
    private static final long CHAIN_EXPIRES = ISSUED + 240 * MINUTE;

    private static StoredSessionToken token(Long replacedAt, Long revokedAt) {
        return new StoredSessionToken(1L, "chain", 7L, "mifos", ISSUED, EXPIRES, CHAIN_EXPIRES, replacedAt, revokedAt);
    }

    @Test
    void renewsInsideItsOwnWindow() {
        assertEquals(RenewalOutcome.RENEW, RenewalPolicy.decide(token(null, null), ISSUED + 5 * MINUTE));
    }

    @Test
    void renewsAfterItsOwnWindowLapsedWhileTheChainIsAlive() {
        // Users stay signed in as long as the Fineract session allows, not 15 minutes.
        assertEquals(RenewalOutcome.RENEW, RenewalPolicy.decide(token(null, null), ISSUED + 90 * MINUTE));
    }

    @Test
    void refusesAtTheChainMaximum() {
        assertEquals(RenewalOutcome.REJECT, RenewalPolicy.decide(token(null, null), CHAIN_EXPIRES));
        assertEquals(RenewalOutcome.REJECT, RenewalPolicy.decide(token(null, null), CHAIN_EXPIRES + 1));
    }

    @Test
    void refusesARevokedToken() {
        assertEquals(RenewalOutcome.REJECT, RenewalPolicy.decide(token(null, ISSUED + MINUTE), ISSUED + 2 * MINUTE));
    }

    @Test
    void endsTheChainWhenARenewedTokenIsPresentedAgain() {
        assertEquals(RenewalOutcome.REVOKE_CHAIN, RenewalPolicy.decide(token(ISSUED + MINUTE, null), ISSUED + 2 * MINUTE));
    }
}
