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

import io.github.zzih.rudder.dolphin.domain.result.PublishResult;
import io.github.zzih.rudder.dolphin.service.publish.strategy.ProjectPublishStrategy;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import io.github.zzih.rudder.publish.api.bundle.WorkflowPublishBundle;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PublishServiceImpl implements PublishService {

    @Resource
    private ProjectPublishStrategy projectPublishStrategy;

    @Override
    public PublishResult publishProject(ProjectPublishBundle bundle) {
        log.info("Publishing project: code={}, name={}", bundle.getProjectCode(), bundle.getProjectName());
        return projectPublishStrategy.publish(bundle, true);
    }

    @Override
    public PublishResult publishWorkflow(WorkflowPublishBundle wfBundle) {
        log.info("Publishing workflow: code={}, name={}",
                wfBundle.getWorkflow().getCode(), wfBundle.getWorkflow().getName());
        ProjectPublishBundle bundle = ProjectPublishBundle.builder()
                .projectCode(wfBundle.getProjectCode())
                .projectName(wfBundle.getProjectName())
                .projectDescription(wfBundle.getProjectDescription())
                .userName(wfBundle.getUserName())
                .datasources(wfBundle.getDatasources())
                .resources(wfBundle.getResources())
                .workflows(List.of(wfBundle.getWorkflow()))
                .build();
        return projectPublishStrategy.publish(bundle, false);
    }
}
