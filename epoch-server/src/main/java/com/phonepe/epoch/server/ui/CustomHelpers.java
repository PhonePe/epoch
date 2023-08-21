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
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.epoch.models.tasks.EpochCompositeTask;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.tasks.EpochTask;
import com.phonepe.epoch.models.tasks.EpochTaskVisitor;
import com.phonepe.epoch.server.utils.IgnoreInJacocoGeneratedReport;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 *
 */
@IgnoreInJacocoGeneratedReport(reason = "UI Helper function. Will revisit TODO")
@UtilityClass
public class CustomHelpers {
    public static Object eqstr(Object lhs, Options options) throws IOException {
        val rhs = options.param(0, null);
        val result = Objects.equals(Objects.toString(lhs), Objects.toString(rhs));
        if (options.tagType == TagType.SECTION) {
            return result ? options.fn() : options.inverse();
        }
        return result
               ? options.hash("yes", true)
               : options.hash("no", false);
    }

    public static Long getresource(String key, EpochTask task) {
        return task.accept(new EpochTaskVisitor<>() {
            @Override
            public Long visit(final EpochCompositeTask composite) {
                return 1L;
            }

            @Override
            public Long visit(final EpochContainerExecutionTask containerExecution) {
                return containerExecution.getResources().stream()
                        .filter(resourceRequirement -> resourceRequirement.getType().name().equals(key))
                        .map(resourceRequirement -> resourceRequirement
                                .accept(new ResourceRequirementVisitor<Long>() {
                                    @Override
                                    public Long visit(final CPURequirement cpuRequirement) {
                                        return cpuRequirement.getCount();
                                    }

                                    @Override
                                    public Long visit(final MemoryRequirement memoryRequirement) {
                                        return memoryRequirement.getSizeInMB();
                                    }
                                }))
                        .findFirst().orElse(1L);
            }
        });
    }

    public static CharSequence env(final String envVar) {
        return System.getenv(envVar);
    }

    public static String join(final CharSequence delimited, final List<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return "";
        }
        return String.join(delimited, strings);
    }
}
