/*
 * Copyright 2021. Santanu Sinha
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */

package com.phonepe.epoch.server.ui;

import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.TagType;
import com.phonepe.epoch.server.utils.IgnoreInJacocoGeneratedReport;
import lombok.val;

import java.io.IOException;
import java.util.Objects;

/**
 *
 */
@IgnoreInJacocoGeneratedReport
public class CustomHelpers {
    public static Object eqstr(Object lhs, Options options) throws IOException {
        val rhs = options.param(0, null);
        boolean result = Objects.equals(Objects.toString(lhs), Objects.toString(rhs));
        if (options.tagType == TagType.SECTION) {
            return result ? options.fn() : options.inverse();
        }
        return result
               ? options.hash("yes", true)
               : options.hash("no", false);
    }

    public static CharSequence env(final String envVar) {
        return System.getenv(envVar);
    }
}
