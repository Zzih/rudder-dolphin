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

package io.github.zzih.arion.dolphin.service.task;

import io.github.zzih.arion.dolphin.common.exception.BizException;
import io.github.zzih.arion.dolphin.domain.qo.TaskDefinitionParam;
import io.github.zzih.arion.dolphin.service.enums.PublishErrorCode;
import io.github.zzih.arion.dolphin.service.task.builder.TaskParamsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts a list of {@link TaskDefinitionParam} (from the API caller) into the
 * taskDefinitionJson string that DolphinScheduler expects.
 * <p>
 * Each task definition is enriched with:
 * <ul>
 *   <li>DS-compatible taskType (mapped via {@link ArionTaskType})</li>
 *   <li>Properly structured taskParams (built by {@link TaskParamsBuilder})</li>
 *   <li>Required default fields (code, flag, priority, timeout, etc.)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDefinitionConverter {

    private static final int DEFAULT_VERSION = 1;
    private static final String DEFAULT_FLAG = "YES";
    private static final String DEFAULT_TASK_PRIORITY = "MEDIUM";
    private static final String DEFAULT_WORKER_GROUP = "default";
    private static final long DEFAULT_ENVIRONMENT_CODE = -1L;
    private static final int DEFAULT_FAIL_RETRY_TIMES = 0;
    private static final int DEFAULT_FAIL_RETRY_INTERVAL = 1;
    private static final String DEFAULT_TIMEOUT_FLAG = "CLOSE";
    private static final String DEFAULT_TIMEOUT_NOTIFY_STRATEGY = "";
    private static final int DEFAULT_TIMEOUT = 0;
    private static final int DEFAULT_DELAY_TIME = 0;

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    /**
     * Convert a list of TaskDefinitionParam to DS taskDefinitionJson string.
     */
    public String convertToJson(List<TaskDefinitionParam> taskDefinitions) {
        List<Map<String, Object>> dsList = new ArrayList<>();
        for (TaskDefinitionParam param : taskDefinitions) {
            dsList.add(convertOne(param));
        }
        return toJson(dsList);
    }

    /**
     * Convert a single TaskDefinitionParam to a DS-compatible task definition map.
     */
    private Map<String, Object> convertOne(TaskDefinitionParam param) {
        ArionTaskType arionType = ArionTaskType.of(param.getTaskType());
        TaskParamsBuilder builder = applicationContext.getBean(arionType.getBuilderClass());

        Map<String, Object> taskParams = param.getTaskParams();
        if (taskParams == null) {
            taskParams = Map.of();
        }
        Map<String, Object> dsTaskParams = builder.build(taskParams, arionType.getSubType());
        String taskParamsJson = toJson(dsTaskParams);

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("code", 0L);
        def.put("name", param.getName());
        def.put("version", DEFAULT_VERSION);
        def.put("description", param.getDescription() != null ? param.getDescription() : "");
        def.put("taskType", arionType.getDsTaskType());
        def.put("taskParams", taskParamsJson);
        def.put("flag", DEFAULT_FLAG);
        def.put("taskPriority", DEFAULT_TASK_PRIORITY);
        def.put("workerGroup", param.getWorkerGroup() != null ? param.getWorkerGroup() : DEFAULT_WORKER_GROUP);
        def.put("environmentCode", DEFAULT_ENVIRONMENT_CODE);
        def.put("failRetryTimes", param.getRetryTimes() != null ? param.getRetryTimes() : DEFAULT_FAIL_RETRY_TIMES);
        def.put("failRetryInterval",
                param.getRetryInterval() != null ? param.getRetryInterval() : DEFAULT_FAIL_RETRY_INTERVAL);
        def.put("timeoutFlag", param.getTimeout() != null && param.getTimeout() > 0 ? "OPEN" : DEFAULT_TIMEOUT_FLAG);
        def.put("timeoutNotifyStrategy",
                param.getTimeout() != null && param.getTimeout() > 0 ? "WARN" : DEFAULT_TIMEOUT_NOTIFY_STRATEGY);
        def.put("timeout", param.getTimeout() != null ? param.getTimeout() : DEFAULT_TIMEOUT);
        def.put("delayTime", DEFAULT_DELAY_TIME);

        log.debug("Converted task definition: name={}, dsType={}", param.getName(), arionType.getDsTaskType());
        return def;
    }

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Failed to serialize task definition: " + e.getMessage());
        }
    }
}
