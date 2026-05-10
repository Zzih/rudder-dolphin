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
import io.github.zzih.rudder.dolphin.service.env.DatasourceSynchronizer;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.zzih.rudder.publish.api.bundle.DatasourceBundle;
import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 发布前同步环境对象。当前只处理 {@code bundle.datasources} —— 把 Rudder 推过来的数据源完整快照
 * 写到 DolphinScheduler 的数据源注册表里,后续 SQL 任务能按 name 解析到 DS 数据源 id。
 *
 * <p>跑在 {@code PublishBeforeHandler} 之后, {@code OfflineWorkflowHandler} / {@code CreateWorkflowHandler}
 * 之前 —— 创建工作流时数据源必须已经存在。
 */
@Slf4j
@Component
public class EnvSyncHandler extends AbstractPublishHandler {

    @Resource
    private DatasourceSynchronizer datasourceSynchronizer;

    @Override
    public boolean canHandle() {
        ProjectPublishBundle bundle = ThreadParamMapUtils.get(PublishConstants.PROJECT_BUNDLE);
        List<DatasourceBundle> datasources = bundle != null ? bundle.getDatasources() : null;
        return datasources != null && !datasources.isEmpty();
    }

    @Override
    public void handle() {
        ProjectPublishBundle bundle = ThreadParamMapUtils.get(PublishConstants.PROJECT_BUNDLE);
        List<DatasourceBundle> datasources = bundle.getDatasources();
        log.info("Syncing {} datasources before publish", datasources.size());
        DatasourceSynchronizer.Summary summary = datasourceSynchronizer.upsertAll(datasources);
        ThreadParamMapUtils.put(PublishConstants.DATASOURCES_CREATED, summary.created());
        ThreadParamMapUtils.put(PublishConstants.DATASOURCES_UPDATED, summary.updated());
        ThreadParamMapUtils.put(PublishConstants.DATASOURCES_SKIPPED, summary.skipped());
    }
}
