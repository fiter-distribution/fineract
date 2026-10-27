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
 * One row of {@code m_acme_session_token}. Times are epoch milliseconds (UTC). {@code replacedAt} is set when the token
 * was renewed (a new token now stands in for it); {@code revokedAt} when its chain was ended.
 */
public record StoredSessionToken(long id, String chainId, long appUserId, String username, long issuedAt, long expiresAt,
        long chainExpiresAt, Long replacedAt, Long revokedAt) {

    /**
     * Usable to authenticate a request: not revoked, inside both its own and its chain's lifetime, and either not
     * renewed or renewed less than {@code readGraceMillis} ago (Go OS ADR 0032, decision 7: a read already in flight
     * when the token is renewed still succeeds).
     */
    public boolean authenticatesAt(long now, long readGraceMillis) {
        if (revokedAt != null || now >= expiresAt || now >= chainExpiresAt) {
            return false;
        }
        return replacedAt == null || now < replacedAt + readGraceMillis;
    }
}
