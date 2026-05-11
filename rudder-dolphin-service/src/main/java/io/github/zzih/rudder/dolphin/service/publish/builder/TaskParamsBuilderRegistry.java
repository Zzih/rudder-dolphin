/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.zzih.rudder.dolphin.service.publish.builder;

import io.github.zzih.rudder.dolphin.client.model.TaskType;
import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Parses {@code TaskBundle.scriptContent} JSON and dispatches to the {@link TaskParamsBuilder}
 * matching the Rudder TaskType. Spring injects the full builder list — registration is just
 * declaring a {@code @Component} that implements {@link TaskParamsBuilder}.
 */
@Component
@RequiredArgsConstructor
public class TaskParamsBuilderRegistry {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final List<TaskParamsBuilder> builders;
    private final ObjectMapper objectMapper;

    public AbstractParameters build(String scriptContent, TaskType type) {
        Map<String, Object> source = parse(scriptContent);
        for (TaskParamsBuilder builder : builders) {
            if (builder.supports(type)) {
                return builder.build(source, type);
            }
        }
        throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                "No TaskParamsBuilder registered for taskType: " + type);
    }

    private Map<String, Object> parse(String scriptContent) {
        if (scriptContent == null || scriptContent.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(scriptContent, MAP_TYPE);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Invalid scriptContent JSON: " + e.getMessage());
        }
    }
}
