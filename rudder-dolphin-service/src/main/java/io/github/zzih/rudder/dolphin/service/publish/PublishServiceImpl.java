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

package io.github.zzih.rudder.dolphin.service.publish;

import io.github.zzih.rudder.dolphin.domain.dto.ProjectPublishDto;
import io.github.zzih.rudder.dolphin.domain.dto.TaskPublishDto;
import io.github.zzih.rudder.dolphin.service.publish.strategy.ProjectPublishStrategy;
import io.github.zzih.rudder.dolphin.service.publish.strategy.TaskPublishStrategy;

import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PublishServiceImpl implements PublishService {

    @Resource
    private ProjectPublishStrategy projectPublishStrategy;

    @Resource
    private TaskPublishStrategy taskPublishStrategy;

    @Override
    public void publishProject(ProjectPublishDto dto) {
        log.info("Publishing project: {}", dto.getProjectName());
        projectPublishStrategy.publish(dto, true);
    }

    @Override
    public void publishWorkflow(ProjectPublishDto dto) {
        log.info("Publishing workflow to project: {}", dto.getProjectName());
        projectPublishStrategy.publish(dto, false);
    }

    @Override
    public void publishTask(TaskPublishDto dto) {
        log.info("Publishing task to workflow: {} in project: {}", dto.getWorkflowName(), dto.getProjectName());
        taskPublishStrategy.publish(dto, false);
    }
}
