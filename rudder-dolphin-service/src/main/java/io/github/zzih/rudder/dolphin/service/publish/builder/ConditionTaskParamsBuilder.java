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

import java.util.Map;

import org.apache.dolphinscheduler.plugin.task.api.parameters.ConditionsParameters;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/** Pass-through into DS {@link ConditionsParameters}. */
@Component
@RequiredArgsConstructor
public class ConditionTaskParamsBuilder implements TaskParamsBuilder {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(TaskType type) {
        return type == TaskType.CONDITION;
    }

    @Override
    public ConditionsParameters build(Map<String, Object> source, TaskType type) {
        return objectMapper.convertValue(source, ConditionsParameters.class);
    }
}
