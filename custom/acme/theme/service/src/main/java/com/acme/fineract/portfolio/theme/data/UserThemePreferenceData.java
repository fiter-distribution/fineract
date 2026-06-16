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
package com.acme.fineract.portfolio.theme.data;

/**
 * Immutable response shape for {@code GET /v1/themes/preferences/{userId}}. Enums are emitted as their FE-aligned
 * lowercase string form (e.g. {@code "day"}) so the React FE in {@code theme.types.ts} can consume the payload without
 * casing translation.
 */
public record UserThemePreferenceData(Long userId, String skinId, String mode, boolean useSystemMode, Overrides overrides) {

    public record Overrides(String fontSize, boolean reducedMotion, boolean highContrast) {}
}
