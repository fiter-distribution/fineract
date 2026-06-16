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

import com.acme.fineract.portfolio.theme.data.AdminThemeConfigData;
import com.acme.fineract.portfolio.theme.data.ThemeSkinData;
import com.acme.fineract.portfolio.theme.data.UserThemePreferenceData;
import java.io.InputStream;
import java.util.List;

/**
 * Read-only query surface for the theme module. Following Fineract convention, all GET endpoints depend on this
 * interface so caching, projection, and transaction tuning can evolve independently of the write path.
 */
public interface ThemeReadPlatformService {

    UserThemePreferenceData retrieveUserPreference(Long userId);

    List<ThemeSkinData> retrieveAllSkins();

    ThemeSkinData retrieveSkin(String skinKey);

    AdminThemeConfigData retrieveAdminConfig();

    ThemeAssetStream downloadAsset(String assetKey);

    record ThemeAssetStream(InputStream stream, String contentType, String filename) {}
}
