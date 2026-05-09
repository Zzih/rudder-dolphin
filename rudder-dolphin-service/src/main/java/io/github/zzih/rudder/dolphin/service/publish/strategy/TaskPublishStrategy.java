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

package io.github.zzih.rudder.dolphin.service.publish.strategy;

import io.github.zzih.rudder.dolphin.service.publish.handler.ExistWorkflowTaskHandler;
import io.github.zzih.rudder.dolphin.service.publish.handler.OfflineWorkflowHandler;
import io.github.zzih.rudder.dolphin.service.publish.handler.OnlineWorkflowHandler;
import io.github.zzih.rudder.dolphin.service.publish.handler.PublishHandler;
import io.github.zzih.rudder.dolphin.service.publish.handler.UpdateWorkflowTaskListHandler;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
public class TaskPublishStrategy extends AbstractPublishStrategy {

    @Resource
    private ExistWorkflowTaskHandler existWorkflowTaskHandler;

    @Resource
    private OfflineWorkflowHandler offlineWorkflowHandler;

    @Resource
    private UpdateWorkflowTaskListHandler updateWorkflowTaskListHandler;

    @Resource
    private OnlineWorkflowHandler onlineWorkflowHandler;

    @Override
    protected List<PublishHandler> getMiddleHandlers() {
        return List.of(
                existWorkflowTaskHandler,
                offlineWorkflowHandler,
                updateWorkflowTaskListHandler,
                onlineWorkflowHandler);
    }
}
