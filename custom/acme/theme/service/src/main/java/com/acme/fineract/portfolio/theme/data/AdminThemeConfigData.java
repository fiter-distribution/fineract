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

import com.google.gson.JsonElement;

/**
 * Uses Gson {@link JsonElement} (not Jackson {@code JsonNode}) for the branding payload because
 * Fineract's {@code DefaultToApiJsonSerializer} runs on Gson — handing it a Jackson tree node
 * causes Gson to reflect over JsonNode's private fields and emit an unusable
 * {@code {_children, _nodeFactory, _value}} structure.
 */
public record AdminThemeConfigData(String defaultSkinId, String defaultMode, boolean allowUserThemeChange, boolean allowUserModeChange,
        JsonElement globalBranding) {}
