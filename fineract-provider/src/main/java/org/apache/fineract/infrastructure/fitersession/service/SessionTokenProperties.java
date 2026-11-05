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

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code fiter.session-token.*}. */
@Component
@ConditionalOnProperty(name = "fiter.session-token.enabled", havingValue = "true", matchIfMissing = true)
public final class SessionTokenProperties {

    private final Duration lifetime;
    private final Duration maxChain;
    private final Duration readGrace;

    public SessionTokenProperties(@Value("${fiter.session-token.lifetime:PT15M}") Duration lifetime,
            @Value("${fiter.session-token.max-chain:PT12H}") Duration maxChain,
            @Value("${fiter.session-token.read-grace:PT30S}") Duration readGrace) {
        if (lifetime.isNegative() || lifetime.isZero() || maxChain.isNegative() || maxChain.isZero() || readGrace.isNegative()) {
            throw new IllegalArgumentException("fiter.session-token durations must be positive");
        }
        this.lifetime = lifetime;
        this.maxChain = maxChain;
        this.readGrace = readGrace;
    }

    public Duration lifetime() {
        return lifetime;
    }

    public Duration maxChain() {
        return maxChain;
    }

    public Duration readGrace() {
        return readGrace;
    }
}
