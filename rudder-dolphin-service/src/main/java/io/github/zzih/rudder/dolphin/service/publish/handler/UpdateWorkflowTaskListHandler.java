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

package io.github.zzih.rudder.dolphin.service.publish.handler;

import io.github.zzih.rudder.dolphin.common.constants.PublishConstants;
import io.github.zzih.rudder.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.rudder.dolphin.domain.dto.TaskPublishDto;
import io.github.zzih.rudder.dolphin.service.task.TaskDefinitionConverter;

import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.dao.entity.DagData;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UpdateWorkflowTaskListHandler extends AbstractPublishHandler {

    @Resource
    private TaskDefinitionConverter taskDefinitionConverter;

    @Override
    public boolean canHandle() {
        return ThreadParamMapUtils.get(PublishConstants.IS_TASK_PUBLISH, false);
    }

    @Override
    public void handle() {
        TaskPublishDto dto = ThreadParamMapUtils.get(PublishConstants.PROJECT_DATA);
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        List<WorkflowDefinition> updateList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_UPDATE_LIST);

        WorkflowDefinition wd = updateList.get(0);
        log.info("Updating tasks in workflow: name={}, code={}", wd.getName(), wd.getCode());

        String taskDefinitionJson = taskDefinitionConverter.convertToJson(dto.getTaskDefinitions());
        String taskRelationJson = taskDefinitionConverter.toJson(dto.getTaskRelations());

        dolphinSchedulerClient.updateWorkflow(
                projectCode,
                wd.getCode(),
                wd.getName(),
                wd.getDescription() != null ? wd.getDescription() : "",
                wd.getGlobalParams(),
                wd.getTimeout(),
                ReleaseState.OFFLINE,
                taskDefinitionJson,
                taskRelationJson);

        log.info("Tasks updated in workflow: name={}, code={}", wd.getName(), wd.getCode());
    }

    @Override
    public void rollBack() {
        Long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        Map<Long, DagData> oldDagDataMap = ThreadParamMapUtils.get(PublishConstants.OLD_DAG_DATA_MAP);
        List<WorkflowDefinition> updateList = ThreadParamMapUtils.get(PublishConstants.WORKFLOW_UPDATE_LIST);

        if (projectCode == null || oldDagDataMap == null || updateList == null || updateList.isEmpty()) {
            return;
        }

        WorkflowDefinition wd = updateList.get(0);
        DagData oldDag = oldDagDataMap.get(wd.getCode());
        if (oldDag == null) {
            return;
        }
        try {
            restoreWorkflowFromDag(projectCode, wd.getCode(), oldDag);
        } catch (Exception e) {
            log.error("Failed to rollback task update: code={}", wd.getCode(), e);
        }
    }
}
