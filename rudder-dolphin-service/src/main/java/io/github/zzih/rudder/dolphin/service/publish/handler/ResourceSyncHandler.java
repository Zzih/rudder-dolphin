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
import io.github.zzih.rudder.dolphin.service.env.ResourceSynchronizer;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.zzih.rudder.dolphin.client.model.ProjectPublishBundle;
import io.github.zzih.rudder.dolphin.client.model.ResourceBundle;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 把 {@code bundle.resources} 同步到 DolphinScheduler 资源中心。跑在 {@link EnvSyncHandler} 之后、
 * {@code OfflineWorkflowHandler} / {@code CreateWorkflowHandler} 之前 —— 创建工作流时 jar 任务的
 * mainJar 必须已经能在 DS 资源中心解析到 fullName。
 */
@Slf4j
@Component
public class ResourceSyncHandler extends AbstractPublishHandler {

    @Resource
    private ResourceSynchronizer resourceSynchronizer;

    @Override
    public boolean canHandle() {
        ProjectPublishBundle bundle = ThreadParamMapUtils.get(PublishConstants.PROJECT_BUNDLE);
        List<ResourceBundle> resources = bundle != null ? bundle.getResources() : null;
        return resources != null && !resources.isEmpty();
    }

    @Override
    public void handle() {
        ProjectPublishBundle bundle = ThreadParamMapUtils.get(PublishConstants.PROJECT_BUNDLE);
        List<ResourceBundle> resources = bundle.getResources();
        log.info("Syncing {} resources before publish", resources.size());
        ResourceSynchronizer.Summary summary = resourceSynchronizer.upsertAll(resources);
        ThreadParamMapUtils.put(PublishConstants.RESOURCES_UPLOADED, summary.uploaded());
        ThreadParamMapUtils.put(PublishConstants.RESOURCES_SKIPPED, summary.skipped());
    }
}
