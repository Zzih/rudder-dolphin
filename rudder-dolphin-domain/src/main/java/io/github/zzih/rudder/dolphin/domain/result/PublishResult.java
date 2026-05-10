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

package io.github.zzih.rudder.dolphin.domain.result;

import java.util.List;

import lombok.Builder;

/**
 * 一次发布的聚合结果。包含项目动作、数据源同步计数、每个 workflow 的产物 (name + code + 是 create 还是 update),
 * 以及上下线 / schedule 的计数。调用方据此判断"实际改了什么"。
 */
@Builder
public record PublishResult(
        String projectName,
        Long projectCode,
        ProjectOutcome projectOutcome,
        int datasourcesCreated,
        int datasourcesUpdated,
        int datasourcesSkipped,
        int resourcesUploaded,
        int resourcesSkipped,
        List<WorkflowResult> workflows,
        int workflowsOnlined,
        int schedulesCreated,
        int schedulesUpdated) {

    public enum ProjectOutcome {
        CREATED,
        UPDATED,
        UNCHANGED
    }

    @Builder
    public record WorkflowResult(String name, long code, Action action) {

        public enum Action {
            CREATED,
            UPDATED
        }
    }
}
