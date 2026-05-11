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

package io.github.zzih.rudder.dolphin.api.controller;

import io.github.zzih.rudder.dolphin.client.model.ProjectPublishBundle;
import io.github.zzih.rudder.dolphin.client.model.WorkflowPublishBundle;
import io.github.zzih.rudder.dolphin.common.result.Result;
import io.github.zzih.rudder.dolphin.domain.result.PublishResult;
import io.github.zzih.rudder.dolphin.service.publish.PublishService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Publish endpoints — wire format is the Rudder publish contract bundle types
 * (see docs/RUDDER_PUBLISH_CONTRACT.md). Server-side adaptation to DolphinScheduler is delegated
 * to {@link PublishService}.
 */
@Tag(name = "Publish", description = "工作流发布接口")
@RestController
@RequestMapping("/publish")
@RequiredArgsConstructor
public class PublishController {

    private final PublishService publishService;

    @Operation(summary = "项目发布", description = "全量发布项目下所有工作流及调度")
    @PostMapping("/project")
    public Result<PublishResult> publishProject(@RequestBody ProjectPublishBundle bundle) {
        return Result.ok(publishService.publishProject(bundle));
    }

    @Operation(summary = "工作流发布", description = "增量发布单个工作流及调度")
    @PostMapping("/workflow")
    public Result<PublishResult> publishWorkflow(@RequestBody WorkflowPublishBundle bundle) {
        return Result.ok(publishService.publishWorkflow(bundle));
    }
}
