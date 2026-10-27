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

/**
 * Decides a renewal (Go OS ADR 0032, decision 6: rotation on renewal).
 *
 * <ul>
 * <li>A token may be renewed after its own 15-minute window has lapsed, as long as the chain's absolute maximum has not
 * passed, so users stay signed in as long as the Fineract session allows.</li>
 * <li>Each renewal replaces the token presented. Presenting a replaced token again is treated as reuse - the token may
 * have been copied - and ends every token in its chain.</li>
 * </ul>
 */
public final class RenewalPolicy {

    private RenewalPolicy() {}

    public static RenewalOutcome decide(StoredSessionToken token, long now) {
        // A revoked chain is already ended; presenting it again changes nothing.
        if (token.revokedAt() != null) {
            return RenewalOutcome.REJECT;
        }
        // Past the absolute maximum nothing in the chain can authenticate or renew, so there is nothing left to
        // protect.
        if (now >= token.chainExpiresAt()) {
            return RenewalOutcome.REJECT;
        }
        // Already renewed: someone holds a token that should have been discarded.
        if (token.replacedAt() != null) {
            return RenewalOutcome.REVOKE_CHAIN;
        }
        // The token's own 15-minute window is deliberately not checked: renewal after it lapses is allowed.
        return RenewalOutcome.RENEW;
    }
}
