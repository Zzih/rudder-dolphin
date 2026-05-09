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
import io.github.zzih.rudder.dolphin.domain.dto.ProjectPublishDto;

import org.apache.dolphinscheduler.dao.entity.Project;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UpdateProjectHandler extends AbstractPublishHandler {

    @Override
    public boolean canHandle() {
        return !ThreadParamMapUtils.get(PublishConstants.IS_NEW_PROJECT, true)
                && !ThreadParamMapUtils.get(PublishConstants.IS_TASK_PUBLISH, false)
                && ThreadParamMapUtils.get(PublishConstants.IS_FULL_PUBLISH, true);
    }

    @Override
    public void handle() {
        ProjectPublishDto dto = ThreadParamMapUtils.get(PublishConstants.PROJECT_DATA);
        long projectCode = ThreadParamMapUtils.get(PublishConstants.PROJECT_CODE);
        String description = dto.getDescription() != null ? dto.getDescription() : "";

        log.info("Updating project: name={}, code={}", dto.getProjectName(), projectCode);
        dolphinSchedulerClient.updateProject(projectCode, dto.getProjectName(), description);
    }

    @Override
    public void rollBack() {
        Project oldProject = ThreadParamMapUtils.get(PublishConstants.OLD_PROJECT);
        if (oldProject != null) {
            log.info("Rolling back project update: code={}", oldProject.getCode());
            try {
                dolphinSchedulerClient.updateProject(
                        oldProject.getCode(), oldProject.getName(), oldProject.getDescription());
            } catch (Exception e) {
                log.error("Failed to rollback project update: code={}", oldProject.getCode(), e);
            }
        }
    }
}
