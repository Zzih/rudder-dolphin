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

import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.plugin.task.api.enums.DependentType;
import org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.zzih.rudder.dolphin.client.model.TaskType;
import lombok.RequiredArgsConstructor;

/**
 * 把 rudder 传过来的 DEPENDENT task params 翻成 DS {@link DependentParameters}。
 *
 * <p>rudder 侧 DependItem 用 (projectName, workflowDefinitionName, depTaskName) 三元 name 引用;
 * 适配层在这里:
 * <ol>
 *   <li>resolveProjectCode(projectName) → DS project code,写入 {@code projectCode}。</li>
 *   <li>resolveWorkflowCode(projectName, workflowDefinitionName) → DS workflow code,写入 {@code definitionCode}。</li>
 *   <li>depTaskName 留空 → {@code depTaskCode = 0}({@code DEPENDENT_WORKFLOW_CODE}),依赖整个工作流;
 *       否则解析 task name → DS task code,写入 {@code depTaskCode}。</li>
 *   <li>按解析出的 {@code depTaskCode} 推 {@code dependentType} 枚举:0/-1 → {@code DEPENDENT_ON_WORKFLOW},
 *       否则 {@code DEPENDENT_ON_TASK}。</li>
 *   <li>把 rudder name 字段({@code projectName} / {@code workflowDefinitionName} / {@code depTaskName})
 *       从 map 里清掉再 convertValue,免得 DS 反序列化时抱怨多余字段(取决于 ObjectMapper 配置)。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class DependentTaskParamsBuilder implements TaskParamsBuilder {

    private static final long DEPENDENT_WORKFLOW_CODE = 0L;
    private static final long DEPENDENT_ALL_TASK_CODE = -1L;

    private final ObjectMapper objectMapper;
    private final WorkflowResolver workflowResolver;

    @Override
    public boolean supports(TaskType type) {
        return type == TaskType.DEPENDENT;
    }

    @Override
    public DependentParameters build(Map<String, Object> source, TaskType type) {
        translateDependence(source);
        return objectMapper.convertValue(source, DependentParameters.class);
    }

    @SuppressWarnings("unchecked")
    private void translateDependence(Map<String, Object> source) {
        Object dependence = source.get("dependence");
        if (!(dependence instanceof Map<?, ?> depMap)) {
            return;
        }
        Object groups = depMap.get("dependTaskList");
        if (!(groups instanceof List<?> groupList)) {
            return;
        }
        for (Object g : groupList) {
            if (!(g instanceof Map<?, ?> group)) {
                continue;
            }
            Object items = group.get("dependItemList");
            if (!(items instanceof List<?> itemList)) {
                continue;
            }
            for (Object i : itemList) {
                if (i instanceof Map<?, ?> item) {
                    translateItem((Map<String, Object>) item);
                }
            }
        }
    }

    private void translateItem(Map<String, Object> item) {
        String projectName = asString(item.remove("projectName"));
        String workflowName = asString(item.remove("workflowDefinitionName"));
        String taskName = asString(item.remove("depTaskName"));

        if (workflowName == null) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "DEPENDENT dependItem missing workflowDefinitionName");
        }

        Long dsProjectCode = workflowResolver.resolveProjectCode(projectName);
        if (dsProjectCode == null) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "DEPENDENT references project '" + projectName + "' not found on DS");
        }
        Long dsWorkflowCode = workflowResolver.resolveWorkflowCode(projectName, workflowName);
        if (dsWorkflowCode == null) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "DEPENDENT references workflow '" + workflowName + "' in project '"
                            + projectName + "' not found on DS — make sure it is published or pre-exists.");
        }

        long dsTaskCode;
        if (taskName == null) {
            dsTaskCode = DEPENDENT_WORKFLOW_CODE;
        } else {
            Long resolved = workflowResolver.resolveTaskCode(dsProjectCode, dsWorkflowCode, taskName);
            if (resolved == null) {
                throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                        "DEPENDENT references task '" + taskName + "' in workflow '"
                                + workflowName + "' (project '" + projectName + "') not found on DS.");
            }
            dsTaskCode = resolved;
        }

        DependentType dependentType =
                (dsTaskCode == DEPENDENT_WORKFLOW_CODE || dsTaskCode == DEPENDENT_ALL_TASK_CODE)
                        ? DependentType.DEPENDENT_ON_WORKFLOW
                        : DependentType.DEPENDENT_ON_TASK;

        item.put("projectCode", dsProjectCode);
        item.put("definitionCode", dsWorkflowCode);
        item.put("depTaskCode", dsTaskCode);
        item.put("dependentType", dependentType.name());
    }

    private String asString(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
