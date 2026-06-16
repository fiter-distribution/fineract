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
package com.acme.fineract.portfolio.theme.service;

import java.io.InputStream;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;

/**
 * Mutation surface for the theme module. Methods that accept {@link JsonCommand} are dispatched from JSON-based command
 * handlers ({@code @CommandType}), while the asset upload accepts the multipart payload directly because the command
 * framework is JSON-only.
 */
public interface ThemeWritePlatformService {

    CommandProcessingResult updateUserPreference(Long userId, JsonCommand command);

    CommandProcessingResult createSkin(JsonCommand command);

    CommandProcessingResult updateSkin(Long skinId, JsonCommand command);

    CommandProcessingResult deleteSkin(Long skinId);

    CommandProcessingResult updateAdminConfig(JsonCommand command);

    CommandProcessingResult uploadAsset(String assetKey, String originalFilename, String contentType, long size, InputStream content);

    CommandProcessingResult deleteAsset(Long assetId);
}
