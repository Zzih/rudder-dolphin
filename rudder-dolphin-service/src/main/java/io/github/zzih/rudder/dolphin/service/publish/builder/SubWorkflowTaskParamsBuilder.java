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

import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;
import io.github.zzih.rudder.dolphin.service.env.WorkflowResolver;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.dolphinscheduler.plugin.task.api.parameters.SubWorkflowParameters;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.zzih.rudder.task.api.task.enums.TaskType;
import lombok.RequiredArgsConstructor;

/**
 * Pass-through into DS {@link SubWorkflowParameters},把 rudder 传过来的 {@code workflowDefinitionName}
 * 翻成 DS 自己生成的 {@code workflowDefinitionCode}。SUB_WORKFLOW DS 端只支持同项目引用,所以默认在
 * 当前发布项目里查。
 */
@Component
@RequiredArgsConstructor
public class SubWorkflowTaskParamsBuilder implements TaskParamsBuilder {

    private final ObjectMapper objectMapper;
    private final WorkflowResolver workflowResolver;

    @Override
    public boolean supports(TaskType type) {
        return type == TaskType.SUB_WORKFLOW;
    }

    @Override
    public SubWorkflowParameters build(Map<String, Object> source, TaskType type) {
        Object name = source.get("workflowDefinitionName");
        if (name == null || name.toString().isBlank()) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "SUB_WORKFLOW task missing workflowDefinitionName");
        }
        String wfName = name.toString();
        Long dsCode = workflowResolver.resolveWorkflowCode(null, wfName);
        if (dsCode == null) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "SUB_WORKFLOW references workflow '" + wfName
                            + "' which is not found in the current DS project — "
                            + "make sure it is published in the same batch or already exists on DS.");
        }

        Map<String, Object> translated = new LinkedHashMap<>(source);
        translated.remove("workflowDefinitionName");
        translated.put("workflowDefinitionCode", dsCode);
        return objectMapper.convertValue(translated, SubWorkflowParameters.class);
    }
}
