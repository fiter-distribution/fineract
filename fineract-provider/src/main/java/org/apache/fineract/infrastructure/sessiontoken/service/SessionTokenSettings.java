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

import java.time.Duration;

/**
 * Lifetimes, fixed in code: Fineract gets no settings for session tokens (Go OS ADR 0032, fork contract, "No new
 * settings on Fineract"). {@code ttl} is how long one token authenticates requests (15 minutes). {@code maxLifetime} is
 * this server's ceiling on a chain (4 hours); the caller asks for its own maximum at mint time (Go OS sends its
 * {@code fineract_session.ttl}) and gets the smaller of the two. {@code readGrace} is how long a renewed token still
 * authenticates reads (30 seconds; decision 7).
 */
public record SessionTokenSettings(Duration ttl, Duration maxLifetime, Duration readGrace) {

    /** The values Fineract uses. */
    public static final SessionTokenSettings FIXED = new SessionTokenSettings(Duration.ofMinutes(15), Duration.ofHours(4),
            Duration.ofSeconds(30));

    public SessionTokenSettings {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxLifetime == null || maxLifetime.compareTo(ttl) < 0) {
            throw new IllegalArgumentException("maxLifetime must be at least the ttl");
        }
        if (readGrace == null || readGrace.isNegative() || readGrace.compareTo(ttl) >= 0) {
            throw new IllegalArgumentException("readGrace must be zero or more, and shorter than the ttl");
        }
    }
}
