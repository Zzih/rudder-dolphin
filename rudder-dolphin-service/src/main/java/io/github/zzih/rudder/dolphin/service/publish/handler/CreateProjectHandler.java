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

import io.github.zzih.rudder.dolphin.client.model.ProjectPublishBundle;
import io.github.zzih.rudder.dolphin.common.constants.PublishConstants;
import io.github.zzih.rudder.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.rudder.dolphin.domain.result.PublishResult.ProjectOutcome;

import org.apache.dolphinscheduler.dao.entity.Project;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateProjectHandler extends AbstractPublishHandler {

    @Override
    public boolean canHandle() {
        return ThreadParamMapUtils.get(PublishConstants.IS_NEW_PROJECT, false);
    }

    @Override
    public void handle() {
        ProjectPublishBundle bundle = ThreadParamMapUtils.get(PublishConstants.PROJECT_BUNDLE);
        String projectName = ThreadParamMapUtils.get(PublishConstants.PROJECT_NAME);
        String description = bundle.getProjectDescription() != null ? bundle.getProjectDescription() : "";

        log.info("Creating project: {}", projectName);
        Project project = dolphinSchedulerClient.createProject(projectName, description);
        ThreadParamMapUtils.put(PublishConstants.PROJECT_CODE, project.getCode());
        ThreadParamMapUtils.put(PublishConstants.PROJECT_OUTCOME, ProjectOutcome.CREATED);
        log.info("Project created: name={}, code={}", projectName, project.getCode());
    }

    @Override
    public void rollBack() {
        Long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        if (projectCode != null) {
            log.info("Rolling back: deleting project code={}", projectCode);
            try {
                dolphinSchedulerClient.deleteProject(projectCode);
            } catch (Exception e) {
                log.error("Failed to rollback project deletion: code={}", projectCode, e);
            }
        }
    }
}
