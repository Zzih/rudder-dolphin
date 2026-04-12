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

package io.github.zzih.arion.dolphin.api.controller;

import io.github.zzih.arion.dolphin.common.result.Result;
import io.github.zzih.arion.dolphin.domain.dto.ProjectPublishDto;
import io.github.zzih.arion.dolphin.domain.dto.TaskPublishDto;
import io.github.zzih.arion.dolphin.domain.dto.WorkflowPublishDto;
import io.github.zzih.arion.dolphin.domain.qo.ProjectPublishRequest;
import io.github.zzih.arion.dolphin.domain.qo.TaskPublishRequest;
import io.github.zzih.arion.dolphin.domain.qo.WorkflowParam;
import io.github.zzih.arion.dolphin.domain.qo.WorkflowPublishRequest;
import io.github.zzih.arion.dolphin.service.publish.PublishService;

import java.util.Collections;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Publish", description = "工作流发布接口")
@RestController
@RequestMapping("/publish")
@RequiredArgsConstructor
public class PublishController {

    private final PublishService publishService;

    @Operation(summary = "项目发布", description = "全量发布项目下所有工作流及调度")
    @PostMapping("/project")
    public Result<Void> publishProject(@Valid @RequestBody ProjectPublishRequest request) {
        List<WorkflowPublishDto> workflows = request.getWorkflows().stream()
                .map(this::toWorkflowDto)
                .toList();
        publishService.publishProject(
                new ProjectPublishDto(request.getProjectName(), request.getDescription(),
                        request.getUserName(), workflows));
        return Result.ok();
    }

    @Operation(summary = "工作流发布", description = "增量发布单个工作流及调度")
    @PostMapping("/workflow")
    public Result<Void> publishWorkflow(@Valid @RequestBody WorkflowPublishRequest request) {
        publishService.publishWorkflow(
                new ProjectPublishDto(request.getProjectName(), request.getDescription(),
                        request.getUserName(), Collections.singletonList(toWorkflowDto(request.getWorkflow()))));
        return Result.ok();
    }

    @Operation(summary = "任务发布", description = "更新指定工作流的任务列表")
    @PostMapping("/task")
    public Result<Void> publishTask(@Valid @RequestBody TaskPublishRequest request) {
        publishService.publishTask(
                new TaskPublishDto(request.getProjectName(), request.getUserName(), request.getWorkflowName(),
                        request.getTaskDefinitions(), request.getTaskRelations()));
        return Result.ok();
    }

    private WorkflowPublishDto toWorkflowDto(WorkflowParam wf) {
        return WorkflowPublishDto.builder()
                .name(wf.getName())
                .description(wf.getDescription())
                .globalParams(wf.getGlobalParams())
                .timeout(wf.getTimeout())
                .taskDefinitions(wf.getTaskDefinitions())
                .taskRelations(wf.getTaskRelations())
                .schedule(wf.getSchedule())
                .build();
    }
}
