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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.dolphinscheduler.dao.entity.AccessToken;
import org.apache.dolphinscheduler.dao.entity.DagData;
import org.apache.dolphinscheduler.dao.entity.Project;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.springframework.stereotype.Component;

import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PublishBeforeHandler extends AbstractPublishHandler {

    @Override
    public void handle() {
        ProjectPublishBundle bundle = ThreadParamMapUtils.get(PublishConstants.PROJECT_BUNDLE);
        // Fall back to projectCode when projectName is not provided.
        String projectName = bundle.getProjectName() != null
                ? bundle.getProjectName()
                : String.valueOf(bundle.getProjectCode());

        ThreadParamMapUtils.put(PublishConstants.PROJECT_NAME, projectName);
        Map<String, Long> wfNameMap = new ConcurrentHashMap<>();
        ThreadParamMapUtils.put(PublishConstants.PROJECT_WORKFLOW_NAME_MAP, wfNameMap);
        setToken(bundle.getUserName());

        log.info("Starting publish for project: {}, user: {}", projectName, bundle.getUserName());

        Project existingProject = dolphinSchedulerClient.queryProjectByName(projectName);

        if (existingProject == null) {
            ThreadParamMapUtils.put(PublishConstants.IS_NEW_PROJECT, true);
            log.info("Project '{}' does not exist, will create new", projectName);
            return;
        }

        ThreadParamMapUtils.put(PublishConstants.IS_NEW_PROJECT, false);
        ThreadParamMapUtils.put(PublishConstants.PROJECT_CODE, existingProject.getCode());
        ThreadParamMapUtils.put(PublishConstants.OLD_PROJECT, existingProject);

        // The /workflow-definition/list endpoint already returns DagData (workflow + tasks + relations)
        // so one round-trip gives us both the workflow list and the rollback snapshots.
        List<DagData> oldDagDataList = dolphinSchedulerClient.listWorkflowDagData(existingProject.getCode());
        List<WorkflowDefinition> oldWorkflows = new ArrayList<>();
        Map<Long, DagData> oldDagDataMap = new HashMap<>();
        for (DagData dag : oldDagDataList) {
            WorkflowDefinition wd = dag.getWorkflowDefinition();
            if (wd == null) {
                continue;
            }
            oldWorkflows.add(wd);
            oldDagDataMap.put(wd.getCode(), dag);
            wfNameMap.put(wd.getName(), wd.getCode());
        }
        ThreadParamMapUtils.put(PublishConstants.OLD_WORKFLOW_LIST, oldWorkflows);
        ThreadParamMapUtils.put(PublishConstants.OLD_DAG_DATA_MAP, oldDagDataMap);

        log.info("Project '{}' exists with code={}, {} existing workflows",
                projectName, existingProject.getCode(), oldWorkflows.size());
    }

    private void setToken(String userName) {
        if (userName == null) {
            return;
        }
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
