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

import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.DESCRIPTION_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.NAME_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.SKIN_KEY_PARAM;
import static com.acme.fineract.portfolio.theme.api.ThemeApiConstants.SKIN_PARAMS;

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
public final class ThemeSkinCommandFromApiJsonDeserializer {

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public ThemeSkinCommandFromApiJsonDeserializer(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForCreate(final String json) {
        validate(json, true);
    }

    public void validateForUpdate(final String json) {
        validate(json, false);
    }

    private void validate(final String json, final boolean isCreate) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, SKIN_PARAMS);

        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource("themeSkin");
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        // DataValidatorBuilder.reset() returns a NEW builder instance (it does not mutate `v`),
        // so we must capture the returned reference before split validation calls — otherwise the
        // configured parameter/value are lost and we end up with "parameter `null` is mandatory."
        final String skinKey = this.fromApiJsonHelper.extractStringNamed(SKIN_KEY_PARAM, element);
        final DataValidatorBuilder skinKeyChain = v.reset().parameter(SKIN_KEY_PARAM).value(skinKey);
        if (isCreate) skinKeyChain.notBlank();
        skinKeyChain.notExceedingLengthOf(100);

        final String name = this.fromApiJsonHelper.extractStringNamed(NAME_PARAM, element);
        final DataValidatorBuilder nameChain = v.reset().parameter(NAME_PARAM).value(name);
        if (isCreate) nameChain.notBlank();
        nameChain.notExceedingLengthOf(200);

        if (this.fromApiJsonHelper.parameterExists(DESCRIPTION_PARAM, element)) {
            final String description = this.fromApiJsonHelper.extractStringNamed(DESCRIPTION_PARAM, element);
            v.reset().parameter(DESCRIPTION_PARAM).value(description).notExceedingLengthOf(500);
        }

        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
        }
    }
}
