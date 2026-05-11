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

package io.github.zzih.rudder.dolphin.service.publish.adapter;

import io.github.zzih.rudder.dolphin.client.model.TaskType;

/**
 * Rudder {@link TaskType} → DolphinScheduler taskType string.
 *
 * <p>The datasource type for SQL tasks is read from {@link TaskType#getDatasourceType()} directly.
 */
public final class RudderTaskTypeMapping {

    private RudderTaskTypeMapping() {
    }

    public static String toDsTaskType(TaskType type) {
        if (type.isSql()) {
            return "SQL";
        }
        return switch (type) {
            case SPARK_JAR -> "SPARK";
            case FLINK_JAR -> "FLINK";
            case PYTHON -> "PYTHON";
            case SHELL -> "SHELL";
            case HTTP -> "HTTP";
            case SEATUNNEL -> "SEATUNNEL";
            case CONDITION -> "CONDITIONS";
            // DS ≤3.1 叫 "SUB_PROCESS",新版统一成 "SUB_WORKFLOW"(SubWorkflowLogicTaskChannelFactory.NAME)。
            case SUB_WORKFLOW -> "SUB_WORKFLOW";
            case SWITCH -> "SWITCH";
            case DEPENDENT -> "DEPENDENT";
            default -> throw new IllegalStateException("Unhandled rudder task type: " + type);
        };
    }
}
