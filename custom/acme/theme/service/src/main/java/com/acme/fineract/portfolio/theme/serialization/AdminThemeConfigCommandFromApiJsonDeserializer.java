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

import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.ADMIN_CONFIG_PARAMS;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.ALLOW_USER_MODE_CHANGE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.ALLOW_USER_THEME_CHANGE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.DEFAULT_MODE_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.DEFAULT_SKIN_ID_PARAM;

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

@Component
public final class AdminThemeConfigCommandFromApiJsonDeserializer {

    private static final List<String> ALLOWED_MODES = List.of("day", "dusk", "night");

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public AdminThemeConfigCommandFromApiJsonDeserializer(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForUpdate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, ADMIN_CONFIG_PARAMS);

        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource("adminThemeConfig");
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (this.fromApiJsonHelper.parameterExists(DEFAULT_SKIN_ID_PARAM, element)) {
            final String skinId = this.fromApiJsonHelper.extractStringNamed(DEFAULT_SKIN_ID_PARAM, element);
            v.reset().parameter(DEFAULT_SKIN_ID_PARAM).value(skinId).notBlank().notExceedingLengthOf(100);
        }
        if (this.fromApiJsonHelper.parameterExists(DEFAULT_MODE_PARAM, element)) {
            final String mode = this.fromApiJsonHelper.extractStringNamed(DEFAULT_MODE_PARAM, element);
            v.reset().parameter(DEFAULT_MODE_PARAM).value(mode).isOneOfTheseStringValues(ALLOWED_MODES.toArray(String[]::new));
        }
        if (this.fromApiJsonHelper.parameterExists(ALLOW_USER_THEME_CHANGE_PARAM, element)) {
            v.reset().parameter(ALLOW_USER_THEME_CHANGE_PARAM)
                    .value(this.fromApiJsonHelper.extractBooleanNamed(ALLOW_USER_THEME_CHANGE_PARAM, element)).validateForBooleanValue();
        }
        if (this.fromApiJsonHelper.parameterExists(ALLOW_USER_MODE_CHANGE_PARAM, element)) {
            v.reset().parameter(ALLOW_USER_MODE_CHANGE_PARAM)
                    .value(this.fromApiJsonHelper.extractBooleanNamed(ALLOW_USER_MODE_CHANGE_PARAM, element)).validateForBooleanValue();
        }

        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
        }
    }
}
