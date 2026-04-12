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

package io.github.zzih.arion.dolphin.service.publish.handler;

import io.github.zzih.arion.dolphin.common.constants.PublishConstants;
import io.github.zzih.arion.dolphin.common.exception.BizException;
import io.github.zzih.arion.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.arion.dolphin.domain.dto.ProjectPublishDto;
import io.github.zzih.arion.dolphin.domain.dto.TaskPublishDto;
import io.github.zzih.arion.dolphin.service.enums.PublishErrorCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.dao.entity.AccessToken;
import org.apache.dolphinscheduler.dao.entity.DagData;
import org.apache.dolphinscheduler.dao.entity.Project;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PublishBeforeHandler extends AbstractPublishHandler {

    @Override
    public void handle() {
        Object projectData = ThreadParamMapUtils.get(PublishConstants.PROJECT_DATA);
        String projectName;
        String userName;

        if (projectData instanceof ProjectPublishDto dto) {
            projectName = dto.getProjectName();
            userName = dto.getUserName();
        } else if (projectData instanceof TaskPublishDto dto) {
            projectName = dto.getProjectName();
            userName = dto.getUserName();
            ThreadParamMapUtils.put(PublishConstants.IS_TASK_PUBLISH, true);
        } else {
            throw new IllegalArgumentException("Unsupported project data type: " + projectData.getClass());
        }

        ThreadParamMapUtils.put(PublishConstants.PROJECT_NAME, projectName);
        setToken(userName);

        log.info("Starting publish for project: {}, user: {}", projectName, userName);

        Project existingProject = dolphinSchedulerClient.queryProjectByName(projectName);

        if (existingProject == null) {
            boolean isTaskPublish = ThreadParamMapUtils.get(PublishConstants.IS_TASK_PUBLISH, false);
            if (isTaskPublish) {
                throw new BizException(PublishErrorCode.PROJECT_NOT_FOUND,
                        "Project not found: " + projectName);
            }
            ThreadParamMapUtils.put(PublishConstants.IS_NEW_PROJECT, true);
            log.info("Project '{}' does not exist, will create new", projectName);
        } else {
            ThreadParamMapUtils.put(PublishConstants.IS_NEW_PROJECT, false);
            ThreadParamMapUtils.put(PublishConstants.PROJECT_CODE, existingProject.getCode());
            ThreadParamMapUtils.put(PublishConstants.OLD_PROJECT, existingProject);

            List<WorkflowDefinition> oldWorkflows = dolphinSchedulerClient.listWorkflows(existingProject.getCode());
            ThreadParamMapUtils.put(PublishConstants.OLD_WORKFLOW_LIST, oldWorkflows);

            Map<Long, DagData> oldDagDataMap = new HashMap<>();
            for (WorkflowDefinition wd : oldWorkflows) {
                try {
                    DagData dagData = dolphinSchedulerClient.queryWorkflowByCode(
                            existingProject.getCode(), wd.getCode());
                    oldDagDataMap.put(wd.getCode(), dagData);
                } catch (Exception e) {
                    log.warn("Failed to query old DAG data for workflow: {}", wd.getName(), e);
                }
            }
            ThreadParamMapUtils.put(PublishConstants.OLD_DAG_DATA_MAP, oldDagDataMap);

            log.info("Project '{}' exists with code={}, {} existing workflows",
                    projectName, existingProject.getCode(), oldWorkflows.size());
        }
    }

    private void setToken(String userName) {
        User publishUser = dolphinSchedulerClient.listUsers().stream()
                .filter(user -> userName.equals(user.getUserName()))
                .findFirst()
                .orElse(null);
        if (publishUser == null) {
            log.warn("DS user not found for userName={}, will use admin token", userName);
            return;
        }

        List<AccessToken> tokenList = dolphinSchedulerClient.getAccessTokens(publishUser.getId());
        AccessToken accessToken = tokenList.isEmpty() ? null : tokenList.get(0);
        if (accessToken == null) {
            log.warn("DS access token not found for user={}, will use admin token", userName);
            return;
        }

        log.info("Using DS token of user: {}", userName);
        ThreadParamMapUtils.put(PublishConstants.ACCESS_TOKEN, accessToken.getToken());
    }

    @Override
    public void rollBack() {
        log.warn("Publish failed, rolling back...");
    }
}
