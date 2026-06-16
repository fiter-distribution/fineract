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
package com.acme.fineract.portfolio.theme.domain;

/**
 * Per-user font-size override. Persisted as the lowercase API name to match the FE contract.
 */
public enum ThemeFontSize {

    SMALL, NORMAL, LARGE, XLARGE;

    public String apiValue() {
        return name().toLowerCase();
    }

    public static ThemeFontSize fromApiValue(String value) {
        if (value == null) return NORMAL;
        for (ThemeFontSize s : values()) {
            if (s.apiValue().equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown font size: " + value);
    }
}
