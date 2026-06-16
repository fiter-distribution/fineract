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
package com.acme.fineract.portfolio.theme.serialization;

import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.FONT_SIZE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.HIGH_CONTRAST_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.MODE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.OVERRIDES_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.REDUCED_MOTION_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.SKIN_ID_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.USER_PREFERENCE_OVERRIDE_PARAMS;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.USER_PREFERENCE_PARAMS;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.USE_SYSTEM_MODE_PARAM;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validates and parses the JSON body of {@code PUT /v1/themes/preferences/{userId}}. Follows the same pattern as
 * {@code CodeCommandFromApiJsonDeserializer}: unsupported-parameter check, collect-all errors in a
 * {@link DataValidatorBuilder}, throw a single {@link PlatformApiDataValidationException} at the end.
 */
@Component
public final class UserThemePreferenceCommandFromApiJsonDeserializer {

    private static final List<String> ALLOWED_MODES = List.of("day", "dusk", "night");
    private static final List<String> ALLOWED_FONT_SIZES = List.of("small", "normal", "large", "xlarge");

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public UserThemePreferenceCommandFromApiJsonDeserializer(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForUpdate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, USER_PREFERENCE_PARAMS);

        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder baseValidator = new DataValidatorBuilder(errors).resource("userThemePreference");
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (this.fromApiJsonHelper.parameterExists(SKIN_ID_PARAM, element)) {
            final String skinId = this.fromApiJsonHelper.extractStringNamed(SKIN_ID_PARAM, element);
            baseValidator.reset().parameter(SKIN_ID_PARAM).value(skinId).notExceedingLengthOf(100);
        }
        if (this.fromApiJsonHelper.parameterExists(MODE_PARAM, element)) {
            final String mode = this.fromApiJsonHelper.extractStringNamed(MODE_PARAM, element);
            baseValidator.reset().parameter(MODE_PARAM).value(mode).isOneOfTheseStringValues(ALLOWED_MODES.toArray(String[]::new));
        }
        if (this.fromApiJsonHelper.parameterExists(USE_SYSTEM_MODE_PARAM, element)) {
            final Boolean useSystem = this.fromApiJsonHelper.extractBooleanNamed(USE_SYSTEM_MODE_PARAM, element);
            baseValidator.reset().parameter(USE_SYSTEM_MODE_PARAM).value(useSystem).validateForBooleanValue();
        }
        if (this.fromApiJsonHelper.parameterExists(OVERRIDES_PARAM, element)) {
            final JsonElement overrides = element.getAsJsonObject().get(OVERRIDES_PARAM);
            this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, overrides.toString(), USER_PREFERENCE_OVERRIDE_PARAMS);
            if (this.fromApiJsonHelper.parameterExists(FONT_SIZE_PARAM, overrides)) {
                final String fontSize = this.fromApiJsonHelper.extractStringNamed(FONT_SIZE_PARAM, overrides);
                baseValidator.reset().parameter(FONT_SIZE_PARAM).value(fontSize)
                        .isOneOfTheseStringValues(ALLOWED_FONT_SIZES.toArray(String[]::new));
            }
            if (this.fromApiJsonHelper.parameterExists(REDUCED_MOTION_PARAM, overrides)) {
                final Boolean rm = this.fromApiJsonHelper.extractBooleanNamed(REDUCED_MOTION_PARAM, overrides);
                baseValidator.reset().parameter(REDUCED_MOTION_PARAM).value(rm).validateForBooleanValue();
            }
            if (this.fromApiJsonHelper.parameterExists(HIGH_CONTRAST_PARAM, overrides)) {
                final Boolean hc = this.fromApiJsonHelper.extractBooleanNamed(HIGH_CONTRAST_PARAM, overrides);
                baseValidator.reset().parameter(HIGH_CONTRAST_PARAM).value(hc).validateForBooleanValue();
            }
        }

        throwExceptionIfValidationWarningsExist(errors);
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
        }
    }
}
