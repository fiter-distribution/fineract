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

import java.time.OffsetDateTime;

/** One row of {@code m_fiter_session_token}. */
public record SessionTokenRecord(Long id, String tokenHash, String chainId, Long appUserId, String passwordFingerprint,
        OffsetDateTime issuedAt, OffsetDateTime expiresAt, OffsetDateTime chainExpiresAt, OffsetDateTime renewedAt,
        OffsetDateTime revokedAt) {

    SessionTokenRecord successor(String nextHash, OffsetDateTime now, OffsetDateTime nextExpiresAt) {
        return new SessionTokenRecord(null, nextHash, chainId, appUserId, passwordFingerprint, now, nextExpiresAt, chainExpiresAt, null,
                null);
    }
}
