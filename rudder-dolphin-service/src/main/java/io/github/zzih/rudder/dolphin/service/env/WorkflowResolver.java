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

package io.github.zzih.rudder.dolphin.service.env;

import io.github.zzih.rudder.dolphin.common.constants.PublishConstants;
import io.github.zzih.rudder.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.rudder.dolphin.service.client.DolphinSchedulerClient;

import java.util.HashMap;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.DagData;
import org.apache.dolphinscheduler.dao.entity.Project;
import org.apache.dolphinscheduler.dao.entity.TaskDefinition;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Translate rudder-side (projectName, workflowName, taskName) references into the codes DS
 * actually uses on the wire. SUB_WORKFLOW and DEPENDENT builders use this; current-project
 * lookups hit the in-memory name map populated by the publish handlers, cross-project lookups
 * cache DS responses for the duration of one publish.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowResolver {

    /** Cache key for {@code Map<Long projectCode, Map<String name, Long code>>} — cross-project workflow listings. */
    private static final String EXTERNAL_WF_NAME_MAP_CACHE = "EXTERNAL_WF_NAME_MAP_CACHE";

    /** Cache key for {@code Map<Long workflowCode, Map<String taskName, Long taskCode>>} — keyed by workflowCode (globally unique on DS). */
    private static final String TASK_NAME_MAP_CACHE = "TASK_NAME_MAP_CACHE";

    private final DolphinSchedulerClient dolphinSchedulerClient;

    public Long resolveProjectCode(String projectName) {
        String currentName = ThreadParamMapUtils.get(PublishConstants.PROJECT_NAME);
        if (isBlank(projectName) || projectName.equals(currentName)) {
            return ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        }
        Project p = dolphinSchedulerClient.queryProjectByName(projectName);
        return p == null ? null : p.getCode();
    }

    public Long resolveWorkflowCode(String projectName, String workflowName) {
        if (isBlank(workflowName)) {
            return null;
        }
        String currentName = ThreadParamMapUtils.get(PublishConstants.PROJECT_NAME);
        boolean sameProject = isBlank(projectName) || projectName.equals(currentName);

        if (sameProject) {
            Map<String, Long> nameMap = ThreadParamMapUtils.get(PublishConstants.PROJECT_WORKFLOW_NAME_MAP);
            if (nameMap != null && nameMap.containsKey(workflowName)) {
                return nameMap.get(workflowName);
            }
        }

        Long projectCode = resolveProjectCode(projectName);
        if (projectCode == null) {
            return null;
        }
        return externalWorkflowMap(projectCode).get(workflowName);
    }

    public Long resolveTaskCode(long projectCode, long workflowCode, String taskName) {
        if (isBlank(taskName)) {
            return null;
        }
        return taskNameMap(projectCode, workflowCode).get(taskName);
    }

    /** Lazy load + cache {@code projectCode → (workflowName → workflowCode)} for the duration of one publish. */
    @SuppressWarnings("unchecked")
    private Map<String, Long> externalWorkflowMap(long projectCode) {
        Map<Long, Map<String, Long>> cache = ThreadParamMapUtils.get(EXTERNAL_WF_NAME_MAP_CACHE);
        if (cache == null) {
            cache = new HashMap<>();
            ThreadParamMapUtils.put(EXTERNAL_WF_NAME_MAP_CACHE, cache);
        }
        return cache.computeIfAbsent(projectCode, code -> {
            Map<String, Long> map = new HashMap<>();
            for (WorkflowDefinition wf : dolphinSchedulerClient.listWorkflows(code)) {
                map.put(wf.getName(), wf.getCode());
            }
            return map;
        });
    }

    /** Lazy load + cache {@code workflowCode → (taskName → taskCode)}; one DagData fetch per workflow per publish. */
    @SuppressWarnings("unchecked")
    private Map<String, Long> taskNameMap(long projectCode, long workflowCode) {
        Map<Long, Map<String, Long>> cache = ThreadParamMapUtils.get(TASK_NAME_MAP_CACHE);
        if (cache == null) {
            cache = new HashMap<>();
            ThreadParamMapUtils.put(TASK_NAME_MAP_CACHE, cache);
        }
        return cache.computeIfAbsent(workflowCode, code -> {
            Map<String, Long> map = new HashMap<>();
            DagData dag = dolphinSchedulerClient.queryWorkflowByCode(projectCode, code);
            if (dag != null && dag.getTaskDefinitionList() != null) {
                for (TaskDefinition td : dag.getTaskDefinitionList()) {
                    map.put(td.getName(), td.getCode());
                }
            }
            return map;
        });
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
