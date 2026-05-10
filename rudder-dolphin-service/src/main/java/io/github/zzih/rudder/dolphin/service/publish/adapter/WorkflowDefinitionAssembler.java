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

package io.github.zzih.rudder.dolphin.service.publish.adapter;

import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;
import io.github.zzih.rudder.dolphin.service.publish.builder.TaskParamsBuilderRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.dolphinscheduler.common.enums.ConditionType;
import org.apache.dolphinscheduler.common.enums.Flag;
import org.apache.dolphinscheduler.common.enums.Priority;
import org.apache.dolphinscheduler.common.enums.TimeoutFlag;
import org.apache.dolphinscheduler.dao.entity.TaskDefinitionLog;
import org.apache.dolphinscheduler.dao.entity.WorkflowTaskRelationLog;
import org.apache.dolphinscheduler.plugin.task.api.enums.TaskTimeoutStrategy;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.zzih.rudder.publish.api.bundle.EdgeBundle;
import io.github.zzih.rudder.publish.api.bundle.TaskBundle;
import io.github.zzih.rudder.publish.api.bundle.WorkflowBundle;
import lombok.RequiredArgsConstructor;

/**
 * Assembles {@link WorkflowBundle} into the three JSON strings DolphinScheduler's create /
 * update workflow API expects: {@code taskDefinitionJson}, {@code taskRelationJson}, {@code locations}.
 *
 * <p>Composition uses the DS API-layer wire entities ({@link TaskDefinitionLog},
 * {@link WorkflowTaskRelationLog} — the exact targets DS deserialises {@code taskDefinitionJson}
 * and {@code taskRelationJson} into) and its enums ({@link Flag}, {@link Priority},
 * {@link TimeoutFlag}, {@link TaskTimeoutStrategy}, {@link ConditionType}) end-to-end — no
 * ad-hoc maps, so a DS schema change surfaces as a compile error rather than a runtime
 * field-name mismatch.
 */
@Component
@RequiredArgsConstructor
public class WorkflowDefinitionAssembler {

    private static final int DEFAULT_VERSION = 1;
    private static final String DEFAULT_WORKER_GROUP = "default";
    private static final long DEFAULT_ENVIRONMENT_CODE = -1L;
    private static final int DEFAULT_FAIL_RETRY_INTERVAL = 1;

    private final TaskParamsBuilderRegistry taskParamsBuilderRegistry;
    private final DagLocationsAdapter dagLocationsAdapter;
    private final ObjectMapper objectMapper;

    public Assembled assemble(WorkflowBundle wf) {
        return new Assembled(
                buildTaskDefinitionJson(wf.getTasks()),
                buildTaskRelationJson(wf.getTasks(), wf.getEdges()),
                dagLocationsAdapter.toDsLocations(wf.getDagJson()));
    }

    private String buildTaskDefinitionJson(List<TaskBundle> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION, "Workflow has no tasks");
        }
        List<TaskDefinitionLog> dsTasks = new ArrayList<>();
        for (TaskBundle task : tasks) {
            dsTasks.add(buildTaskDefinitionLog(task));
        }
        return toJson(dsTasks);
    }

    private TaskDefinitionLog buildTaskDefinitionLog(TaskBundle task) {
        int timeout = task.getTimeout() != null ? task.getTimeout() : 0;
        AbstractParameters params = taskParamsBuilderRegistry
                .build(task.getScriptContent(), task.getTaskType());

        TaskDefinitionLog def = new TaskDefinitionLog();
        def.setCode(task.getTaskCode() != null ? task.getTaskCode() : 0L);
        def.setName(task.getName());
        def.setVersion(DEFAULT_VERSION);
        def.setDescription(task.getDescription() != null ? task.getDescription() : "");
        def.setTaskType(RudderTaskTypeMapping.toDsTaskType(task.getTaskType()));
        def.setTaskParams(toJson(params));
        def.setFlag(Flag.YES);
        def.setTaskPriority(Priority.MEDIUM);
        def.setWorkerGroup(DEFAULT_WORKER_GROUP);
        def.setEnvironmentCode(DEFAULT_ENVIRONMENT_CODE);
        def.setFailRetryTimes(task.getRetryTimes() != null ? task.getRetryTimes() : 0);
        def.setFailRetryInterval(
                task.getRetryInterval() != null ? task.getRetryInterval() : DEFAULT_FAIL_RETRY_INTERVAL);
        def.setTimeoutFlag(timeout > 0 ? TimeoutFlag.OPEN : TimeoutFlag.CLOSE);
        def.setTimeoutNotifyStrategy(timeout > 0 ? TaskTimeoutStrategy.WARN : null);
        def.setTimeout(timeout);
        def.setDelayTime(0);
        return def;
    }

    private String buildTaskRelationJson(List<TaskBundle> tasks, List<EdgeBundle> edges) {
        List<WorkflowTaskRelationLog> relations = new ArrayList<>();
        Set<Long> tasksWithIncoming = new HashSet<>();
        if (edges != null) {
            for (EdgeBundle edge : edges) {
                if (edge.getTargetTaskCode() == null || edge.getSourceTaskCode() == null) {
                    continue;
                }
                relations.add(buildRelation(edge.getSourceTaskCode(), edge.getTargetTaskCode()));
                tasksWithIncoming.add(edge.getTargetTaskCode());
            }
        }
        // Synthesize root relations (preTaskCode=0) for any task without incoming edges so DS recognises them.
        for (TaskBundle task : tasks) {
            if (task.getTaskCode() == null) {
                continue;
            }
            if (!tasksWithIncoming.contains(task.getTaskCode())) {
                relations.add(buildRelation(0L, task.getTaskCode()));
            }
        }
        return toJson(relations);
    }

    @SuppressWarnings("deprecation") // ConditionType / conditionParams are still required on the wire by DS 3.4
    private WorkflowTaskRelationLog buildRelation(long preTaskCode, long postTaskCode) {
        WorkflowTaskRelationLog relation = new WorkflowTaskRelationLog();
        relation.setName("");
        relation.setPreTaskCode(preTaskCode);
        relation.setPreTaskVersion(DEFAULT_VERSION);
        relation.setPostTaskCode(postTaskCode);
        relation.setPostTaskVersion(DEFAULT_VERSION);
        relation.setConditionType(ConditionType.NONE);
        relation.setConditionParams("{}");
        return relation;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Failed to serialize workflow component: " + e.getMessage());
        }
    }

    public record Assembled(String taskDefinitionJson, String taskRelationJson, String locations) {
    }
}
