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

package io.github.zzih.rudder.dolphin.service.task;

import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;
import io.github.zzih.rudder.dolphin.service.task.builder.*;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RudderDolphinTaskType {

    // SQL tasks
    HIVE_SQL("SQL", "HIVE", SqlTaskParamsBuilder.class),
    STARROCKS_SQL("SQL", "STARROCKS", SqlTaskParamsBuilder.class),
    MYSQL_SQL("SQL", "MYSQL", SqlTaskParamsBuilder.class),
    TRINO_SQL("SQL", "TRINO", SqlTaskParamsBuilder.class),

    // Spark tasks
    SPARK_SQL("SPARK", "SPARK_SQL", SparkTaskParamsBuilder.class),
    SPARK_JAR("SPARK", "SPARK_JAR", SparkTaskParamsBuilder.class),

    // Flink tasks
    FLINK_SQL("FLINK", "FLINK_SQL", FlinkTaskParamsBuilder.class),
    FLINK_JAR("FLINK", "FLINK_JAR", FlinkTaskParamsBuilder.class),

    // Script tasks
    SHELL("SHELL", null, ShellTaskParamsBuilder.class),
    PYTHON("PYTHON", null, PythonTaskParamsBuilder.class),

    // Data integration
    SEATUNNEL("SEATUNNEL", null, SeatunnelTaskParamsBuilder.class),

    // Control flow tasks
    DEPENDENT("DEPENDENT", null, DependentTaskParamsBuilder.class),
    CONDITION("CONDITIONS", null, ConditionTaskParamsBuilder.class),
    SUB_WORKFLOW("SUB_PROCESS", null, SubWorkflowTaskParamsBuilder.class),
    SWITCH("SWITCH", null, SwitchTaskParamsBuilder.class);

    /**
     * DolphinScheduler task type name
     */
    private final String dsTaskType;

    /**
     * Sub-type hint passed to the builder (e.g., datasource type for SQL, SPARK_SQL vs SPARK_JAR)
     */
    private final String subType;

    /**
     * Spring bean class for building DS task params
     */
    private final Class<? extends TaskParamsBuilder> builderClass;

    public static RudderDolphinTaskType of(String name) {
        for (RudderDolphinTaskType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                "Unsupported task type: " + name);
    }
}
