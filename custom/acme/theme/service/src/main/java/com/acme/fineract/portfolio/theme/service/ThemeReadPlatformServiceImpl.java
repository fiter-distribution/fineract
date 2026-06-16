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
import com.acme.fineract.portfolio.theme.domain.AdminThemeConfig;
import com.acme.fineract.portfolio.theme.domain.AdminThemeConfigRepository;
import com.acme.fineract.portfolio.theme.domain.ThemeAsset;
import com.acme.fineract.portfolio.theme.domain.ThemeAssetRepository;
import com.acme.fineract.portfolio.theme.domain.ThemeMode;
import com.acme.fineract.portfolio.theme.domain.ThemeSkin;
import com.acme.fineract.portfolio.theme.domain.ThemeSkinRepository;
import com.acme.fineract.portfolio.theme.domain.UserThemePreference;
import com.acme.fineract.portfolio.theme.domain.UserThemePreferenceRepository;
import com.acme.fineract.portfolio.theme.exception.ThemeAssetNotFoundException;
import com.acme.fineract.portfolio.theme.exception.ThemeSkinNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.List;
import org.apache.fineract.infrastructure.contentstore.service.ContentStoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemeReadPlatformServiceImpl implements ThemeReadPlatformService {

    private final UserThemePreferenceRepository userPreferenceRepo;
    private final ThemeSkinRepository skinRepo;
    private final AdminThemeConfigRepository configRepo;
    private final ThemeAssetRepository assetRepo;
    private final ContentStoreService contentStore;

    public ThemeReadPlatformServiceImpl(UserThemePreferenceRepository userPreferenceRepo, ThemeSkinRepository skinRepo,
            AdminThemeConfigRepository configRepo, ThemeAssetRepository assetRepo, ContentStoreService contentStore) {
        this.userPreferenceRepo = userPreferenceRepo;
        this.skinRepo = skinRepo;
        this.configRepo = configRepo;
        this.assetRepo = assetRepo;
        this.contentStore = contentStore;
    }

    @Override
    @Transactional(readOnly = true)
    public UserThemePreferenceData retrieveUserPreference(final Long userId) {
        return userPreferenceRepo.findByUserId(userId).map(this::toData).orElseGet(() -> defaultPreference(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThemeSkinData> retrieveAllSkins() {
        return skinRepo.findAll().stream().map(this::toSkinData).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ThemeSkinData retrieveSkin(final String skinKey) {
        return skinRepo.findBySkinKey(skinKey).map(this::toSkinData).orElseThrow(() -> new ThemeSkinNotFoundException(skinKey));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminThemeConfigData retrieveAdminConfig() {
        AdminThemeConfig entity = configRepo.findAll().stream().findFirst().orElseGet(AdminThemeConfig::new);
        return toAdminConfigData(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ThemeAssetStream downloadAsset(final String assetKey) {
        ThemeAsset asset = assetRepo.findByAssetKey(assetKey).orElseThrow(() -> new ThemeAssetNotFoundException(assetKey));
        return new ThemeAssetStream(contentStore.download(asset.getStoragePath()), asset.getContentType(), asset.getOriginalFilename());
    }

    private UserThemePreferenceData toData(UserThemePreference e) {
        return new UserThemePreferenceData(e.getUserId(), e.getSkinId(), e.getMode().apiValue(), e.isUseSystemMode(),
                new UserThemePreferenceData.Overrides(e.getFontSize().apiValue(), e.isReducedMotion(), e.isHighContrast()));
    }

    private UserThemePreferenceData defaultPreference(final Long userId) {
        AdminThemeConfigData admin = retrieveAdminConfig();
        return new UserThemePreferenceData(userId, admin.defaultSkinId(), admin.defaultMode(), false,
                new UserThemePreferenceData.Overrides("normal", false, false));
    }

    private ThemeSkinData toSkinData(ThemeSkin e) {
        return new ThemeSkinData(e.getId(), e.getSkinKey(), e.getName(), e.getDescription(), toGson(e.getColors()), toGson(e.getModes()),
                toGson(e.getTypography()), toGson(e.getBranding()), toGson(e.getBorderRadius()), toGson(e.getShadows()), e.isSystem());
    }

    private AdminThemeConfigData toAdminConfigData(AdminThemeConfig e) {
        ThemeMode mode = e.getDefaultMode() == null ? ThemeMode.DAY : e.getDefaultMode();
        return new AdminThemeConfigData(e.getDefaultSkinId(), mode.apiValue(), e.isAllowUserThemeChange(), e.isAllowUserModeChange(),
                toGson(e.getGlobalBranding()));
    }

    /**
     * Bridge Jackson {@link JsonNode} (used for JPA persistence via the custom
     * {@code JsonNodeAttributeConverter}) to Gson {@link JsonElement} (consumed by Fineract's
     * {@code DefaultToApiJsonSerializer}). Without this bridge Gson reflects over JsonNode's
     * private fields and emits a {@code {_children, _nodeFactory, _value}} mess on the wire.
     */
    private JsonElement toGson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return JsonParser.parseString(node.toString());
    }
}
