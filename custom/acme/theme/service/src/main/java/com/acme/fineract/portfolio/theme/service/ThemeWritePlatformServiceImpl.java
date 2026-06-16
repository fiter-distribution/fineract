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

import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.ALLOW_USER_MODE_CHANGE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.ALLOW_USER_THEME_CHANGE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.BORDER_RADIUS_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.BRANDING_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.COLORS_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.DEFAULT_MODE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.DEFAULT_SKIN_ID_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.DESCRIPTION_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.FONT_SIZE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.GLOBAL_BRANDING_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.HIGH_CONTRAST_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.MODES_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.MODE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.NAME_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.OVERRIDES_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.REDUCED_MOTION_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.SHADOWS_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.SKIN_ID_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.SKIN_KEY_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.TYPOGRAPHY_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.USE_SYSTEM_MODE_PARAM;

import com.acme.fineract.portfolio.theme.domain.AdminThemeConfig;
import com.acme.fineract.portfolio.theme.domain.AdminThemeConfigRepository;
import com.acme.fineract.portfolio.theme.domain.ThemeAsset;
import com.acme.fineract.portfolio.theme.domain.ThemeAssetRepository;
import com.acme.fineract.portfolio.theme.domain.ThemeFontSize;
import com.acme.fineract.portfolio.theme.domain.ThemeMode;
import com.acme.fineract.portfolio.theme.domain.ThemeSkin;
import com.acme.fineract.portfolio.theme.domain.ThemeSkinRepository;
import com.acme.fineract.portfolio.theme.domain.UserThemePreference;
import com.acme.fineract.portfolio.theme.domain.UserThemePreferenceRepository;
import com.acme.fineract.portfolio.theme.exception.SystemThemeSkinCannotBeDeletedException;
import com.acme.fineract.portfolio.theme.exception.ThemeAssetNotFoundException;
import com.acme.fineract.portfolio.theme.exception.ThemeSkinAlreadyExistsException;
import com.acme.fineract.portfolio.theme.exception.ThemeSkinNotFoundException;
import com.acme.fineract.portfolio.theme.serialization.AdminThemeConfigCommandFromApiJsonDeserializer;
import com.acme.fineract.portfolio.theme.serialization.ThemeSkinCommandFromApiJsonDeserializer;
import com.acme.fineract.portfolio.theme.serialization.UserThemePreferenceCommandFromApiJsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.infrastructure.contentstore.service.ContentStoreService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemeWritePlatformServiceImpl implements ThemeWritePlatformService {

    private static final Set<String> ALLOWED_ASSET_TYPES = Set.of("image/png", "image/jpeg", "image/jpg");
    private static final Set<String> ALLOWED_ASSET_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final long MAX_ASSET_BYTES = 5L * 1024 * 1024;
    private static final String ASSET_STORAGE_PREFIX = "themes";
    private static final ObjectMapper JACKSON = new ObjectMapper();

    private final UserThemePreferenceRepository userPreferenceRepo;
    private final ThemeSkinRepository skinRepo;
    private final AdminThemeConfigRepository configRepo;
    private final ThemeAssetRepository assetRepo;
    private final UserThemePreferenceCommandFromApiJsonDeserializer userPrefDeserializer;
    private final ThemeSkinCommandFromApiJsonDeserializer skinDeserializer;
    private final AdminThemeConfigCommandFromApiJsonDeserializer configDeserializer;
    private final FromJsonHelper fromApiJsonHelper;
    private final ContentStoreService contentStore;

    public ThemeWritePlatformServiceImpl(UserThemePreferenceRepository userPreferenceRepo, ThemeSkinRepository skinRepo,
            AdminThemeConfigRepository configRepo, ThemeAssetRepository assetRepo,
            UserThemePreferenceCommandFromApiJsonDeserializer userPrefDeserializer,
            ThemeSkinCommandFromApiJsonDeserializer skinDeserializer, AdminThemeConfigCommandFromApiJsonDeserializer configDeserializer,
            FromJsonHelper fromApiJsonHelper, ContentStoreService contentStore) {
        this.userPreferenceRepo = userPreferenceRepo;
        this.skinRepo = skinRepo;
        this.configRepo = configRepo;
        this.assetRepo = assetRepo;
        this.userPrefDeserializer = userPrefDeserializer;
        this.skinDeserializer = skinDeserializer;
        this.configDeserializer = configDeserializer;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.contentStore = contentStore;
    }

    // ---------- User theme preference ----------

    @Override
    @Transactional
    public CommandProcessingResult updateUserPreference(final Long userId, final JsonCommand command) {
        userPrefDeserializer.validateForUpdate(command.json());

        UserThemePreference entity = userPreferenceRepo.findByUserId(userId).orElseGet(() -> new UserThemePreference(userId));
        Map<String, Object> changes = new LinkedHashMap<>();

        applyStringIfPresent(command, SKIN_ID_PARAM, entity::getSkinId, entity::setSkinId, changes);
        if (command.parameterExists(MODE_PARAM)) {
            ThemeMode newMode = ThemeMode.fromApiValue(command.stringValueOfParameterNamed(MODE_PARAM));
            if (entity.getMode() != newMode) {
                changes.put(MODE_PARAM, newMode.apiValue());
                entity.setMode(newMode);
            }
        }
        if (command.parameterExists(USE_SYSTEM_MODE_PARAM)) {
            boolean newValue = command.booleanPrimitiveValueOfParameterNamed(USE_SYSTEM_MODE_PARAM);
            if (entity.isUseSystemMode() != newValue) {
                changes.put(USE_SYSTEM_MODE_PARAM, newValue);
                entity.setUseSystemMode(newValue);
            }
        }
        if (command.parameterExists(OVERRIDES_PARAM)) {
            JsonElement overrides = command.parsedJson().getAsJsonObject().get(OVERRIDES_PARAM);
            if (fromApiJsonHelper.parameterExists(FONT_SIZE_PARAM, overrides)) {
                ThemeFontSize newSize = ThemeFontSize.fromApiValue(fromApiJsonHelper.extractStringNamed(FONT_SIZE_PARAM, overrides));
                if (entity.getFontSize() != newSize) {
                    changes.put(FONT_SIZE_PARAM, newSize.apiValue());
                    entity.setFontSize(newSize);
                }
            }
            if (fromApiJsonHelper.parameterExists(REDUCED_MOTION_PARAM, overrides)) {
                Boolean newValue = fromApiJsonHelper.extractBooleanNamed(REDUCED_MOTION_PARAM, overrides);
                if (newValue != null && entity.isReducedMotion() != newValue) {
                    changes.put(REDUCED_MOTION_PARAM, newValue);
                    entity.setReducedMotion(newValue);
                }
            }
            if (fromApiJsonHelper.parameterExists(HIGH_CONTRAST_PARAM, overrides)) {
                Boolean newValue = fromApiJsonHelper.extractBooleanNamed(HIGH_CONTRAST_PARAM, overrides);
                if (newValue != null && entity.isHighContrast() != newValue) {
                    changes.put(HIGH_CONTRAST_PARAM, newValue);
                    entity.setHighContrast(newValue);
                }
            }
        }

        UserThemePreference saved = userPreferenceRepo.save(entity);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(saved.getId()).with(changes).build();
    }

    // ---------- Theme skin ----------

    @Override
    @Transactional
    public CommandProcessingResult createSkin(final JsonCommand command) {
        skinDeserializer.validateForCreate(command.json());
        String skinKey = command.stringValueOfParameterNamed(SKIN_KEY_PARAM);
        if (skinRepo.existsBySkinKey(skinKey)) {
            throw new ThemeSkinAlreadyExistsException(skinKey);
        }
        ThemeSkin entity = new ThemeSkin();
        entity.setSkinKey(skinKey);
        applySkinFields(command, entity);
        ThemeSkin saved = skinRepo.save(entity);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(saved.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateSkin(final Long skinId, final JsonCommand command) {
        skinDeserializer.validateForUpdate(command.json());
        ThemeSkin entity = skinRepo.findById(skinId).orElseThrow(() -> new ThemeSkinNotFoundException(String.valueOf(skinId)));
        applySkinFields(command, entity);
        ThemeSkin saved = skinRepo.save(entity);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(saved.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteSkin(final Long skinId) {
        ThemeSkin entity = skinRepo.findById(skinId).orElseThrow(() -> new ThemeSkinNotFoundException(String.valueOf(skinId)));
        if (entity.isSystem()) {
            throw new SystemThemeSkinCannotBeDeletedException(entity.getSkinKey());
        }
        skinRepo.delete(entity);
        return new CommandProcessingResultBuilder().withEntityId(skinId).build();
    }

    private void applySkinFields(JsonCommand command, ThemeSkin entity) {
        if (command.parameterExists(NAME_PARAM)) entity.setName(command.stringValueOfParameterNamed(NAME_PARAM));
        if (command.parameterExists(DESCRIPTION_PARAM)) entity.setDescription(command.stringValueOfParameterNamed(DESCRIPTION_PARAM));
        entity.setColors(extractJsonNode(command, COLORS_PARAM));
        entity.setModes(extractJsonNode(command, MODES_PARAM));
        entity.setTypography(extractJsonNode(command, TYPOGRAPHY_PARAM));
        entity.setBranding(extractJsonNode(command, BRANDING_PARAM));
        entity.setBorderRadius(extractJsonNode(command, BORDER_RADIUS_PARAM));
        entity.setShadows(extractJsonNode(command, SHADOWS_PARAM));
    }

    private JsonNode extractJsonNode(JsonCommand command, String paramName) {
        if (!command.parameterExists(paramName)) return null;
        JsonElement element = command.parsedJson().getAsJsonObject().get(paramName);
        if (element == null || element.isJsonNull()) return null;
        try {
            return JACKSON.readTree(element.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JSON parameter '" + paramName + "'", e);
        }
    }

    // ---------- Admin theme config ----------

    @Override
    @Transactional
    public CommandProcessingResult updateAdminConfig(final JsonCommand command) {
        configDeserializer.validateForUpdate(command.json());
        AdminThemeConfig entity = configRepo.findAll().stream().findFirst().orElseGet(AdminThemeConfig::new);
        Map<String, Object> changes = new LinkedHashMap<>();

        applyStringIfPresent(command, DEFAULT_SKIN_ID_PARAM, entity::getDefaultSkinId, entity::setDefaultSkinId, changes);
        if (command.parameterExists(DEFAULT_MODE_PARAM)) {
            ThemeMode newMode = ThemeMode.fromApiValue(command.stringValueOfParameterNamed(DEFAULT_MODE_PARAM));
            if (entity.getDefaultMode() != newMode) {
                changes.put(DEFAULT_MODE_PARAM, newMode.apiValue());
                entity.setDefaultMode(newMode);
            }
        }
        if (command.parameterExists(ALLOW_USER_THEME_CHANGE_PARAM)) {
            boolean v = command.booleanPrimitiveValueOfParameterNamed(ALLOW_USER_THEME_CHANGE_PARAM);
            if (entity.isAllowUserThemeChange() != v) {
                changes.put(ALLOW_USER_THEME_CHANGE_PARAM, v);
                entity.setAllowUserThemeChange(v);
            }
        }
        if (command.parameterExists(ALLOW_USER_MODE_CHANGE_PARAM)) {
            boolean v = command.booleanPrimitiveValueOfParameterNamed(ALLOW_USER_MODE_CHANGE_PARAM);
            if (entity.isAllowUserModeChange() != v) {
                changes.put(ALLOW_USER_MODE_CHANGE_PARAM, v);
                entity.setAllowUserModeChange(v);
            }
        }
        if (command.parameterExists(GLOBAL_BRANDING_PARAM)) {
            entity.setGlobalBranding(extractJsonNode(command, GLOBAL_BRANDING_PARAM));
            changes.put(GLOBAL_BRANDING_PARAM, "updated");
        }

        AdminThemeConfig saved = configRepo.save(entity);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(saved.getId()).with(changes).build();
    }

    // ---------- Theme asset ----------

    @Override
    @Transactional
    public CommandProcessingResult uploadAsset(final String assetKey, final String originalFilename, final String contentType,
            final long size, final InputStream content) {
        if (assetKey == null || assetKey.isBlank()) {
            throw new GeneralPlatformDomainRuleException("error.msg.theme.asset.key.required", "Asset key is required.");
        }
        if (!ALLOWED_ASSET_TYPES.contains(contentType)) {
            throw new GeneralPlatformDomainRuleException("error.msg.theme.asset.content.type.unsupported",
                    "Unsupported content type: " + contentType + ". Allowed: " + ALLOWED_ASSET_TYPES, contentType);
        }
        if (size > MAX_ASSET_BYTES) {
            throw new GeneralPlatformDomainRuleException("error.msg.theme.asset.too.large",
                    "File too large; max " + MAX_ASSET_BYTES + " bytes.", size);
        }
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_ASSET_EXTENSIONS.contains(extension)) {
            throw new GeneralPlatformDomainRuleException("error.msg.theme.asset.extension.unsupported",
                    "Unsupported file extension `." + extension + "`. Allowed: " + ALLOWED_ASSET_EXTENSIONS, extension);
        }

        // Upsert in place. Delete-then-insert on the same unique key triggers a race in EclipseLink:
        // batched INSERT can be flushed before the DELETE, hitting the unique constraint. Keeping the
        // row and overwriting its columns side-steps the issue entirely and preserves audit history.
        final ThemeAsset entity = assetRepo.findByAssetKey(assetKey).orElseGet(ThemeAsset::new);
        final String previousStoragePath = entity.getStoragePath();

        final String relativePath = ASSET_STORAGE_PREFIX + "/" + UUID.randomUUID() + "." + extension;
        final String stored = contentStore.upload(relativePath, content, contentType);

        entity.setAssetKey(assetKey);
        entity.setStoragePath(stored);
        entity.setOriginalFilename(originalFilename);
        entity.setContentType(contentType);
        entity.setFileSize(size);
        final ThemeAsset saved = assetRepo.save(entity);

        if (previousStoragePath != null && !previousStoragePath.equals(stored)) {
            try {
                contentStore.delete(previousStoragePath);
            } catch (RuntimeException ignored) {
                // Tolerate: orphan files can be reaped by a cleanup job. The DB now points at the
                // new bytes, which is what callers care about.
            }
        }

        return new CommandProcessingResultBuilder().withEntityId(saved.getId())
                .with(Map.of("assetKey", assetKey, "url", "/themes/assets/" + assetKey)).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteAsset(final Long assetId) {
        ThemeAsset entity = assetRepo.findById(assetId).orElseThrow(() -> new ThemeAssetNotFoundException(String.valueOf(assetId)));
        try {
            contentStore.delete(entity.getStoragePath());
        } catch (RuntimeException ignored) {
            // tolerate; DB row removal is the primary outcome
        }
        assetRepo.delete(entity);
        return new CommandProcessingResultBuilder().withEntityId(assetId).build();
    }

    private void applyStringIfPresent(JsonCommand command, String param, java.util.function.Supplier<String> getter,
            java.util.function.Consumer<String> setter, Map<String, Object> changes) {
        if (command.parameterExists(param)) {
            String newValue = command.stringValueOfParameterNamed(param);
            if (!java.util.Objects.equals(getter.get(), newValue)) {
                changes.put(param, newValue);
                setter.accept(newValue);
            }
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
