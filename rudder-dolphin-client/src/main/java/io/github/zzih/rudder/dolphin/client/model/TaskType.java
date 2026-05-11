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

package io.github.zzih.rudder.dolphin.client.model;

import static io.github.zzih.rudder.dolphin.client.model.ExecutionMode.BATCH;
import static io.github.zzih.rudder.dolphin.client.model.ExecutionMode.STREAMING;
import static io.github.zzih.rudder.dolphin.client.model.TaskCategory.API;
import static io.github.zzih.rudder.dolphin.client.model.TaskCategory.CONTROL;
import static io.github.zzih.rudder.dolphin.client.model.TaskCategory.DATA_INTEGRATION;
import static io.github.zzih.rudder.dolphin.client.model.TaskCategory.JAR;
import static io.github.zzih.rudder.dolphin.client.model.TaskCategory.SCRIPT;
import static io.github.zzih.rudder.dolphin.client.model.TaskCategory.SQL;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Rudder publish contract — TaskType enum. Mirror of Rudder's {@code io.github.zzih.rudder.task.api.task.enums.TaskType}.
 * Enum names must stay in sync (JSON wire format relies on {@link #name()}).
 */
@Getter
@AllArgsConstructor
public enum TaskType {

    HIVE_SQL("Hive SQL", ".sql", SQL, true, "HIVE", List.of(BATCH)),
    STARROCKS_SQL("StarRocks SQL", ".sql", SQL, true, "STARROCKS", List.of(BATCH)),
    MYSQL("MySQL", ".sql", SQL, true, "MYSQL", List.of(BATCH)),
    DORIS_SQL("Doris SQL", ".sql", SQL, true, "DORIS", List.of(BATCH)),
    POSTGRES_SQL("PostgreSQL", ".sql", SQL, true, "POSTGRES", List.of(BATCH)),
    CLICKHOUSE_SQL("ClickHouse SQL", ".sql", SQL, true, "CLICKHOUSE", List.of(BATCH)),
    TRINO_SQL("Trino SQL", ".sql", SQL, true, "TRINO", List.of(BATCH)),
    SPARK_SQL("Spark SQL", ".sql", SQL, true, "SPARK", List.of(BATCH)),
    FLINK_SQL("Flink SQL", ".sql", SQL, true, "FLINK", List.of(BATCH, STREAMING)),

    SPARK_JAR("Spark JAR", ".json", JAR, false, null, List.of(BATCH)),
    FLINK_JAR("Flink JAR", ".json", JAR, false, null, List.of(BATCH, STREAMING)),

    PYTHON("Python", ".py", SCRIPT, false, null, List.of(BATCH)),
    SHELL("Shell", ".sh", SCRIPT, false, null, List.of(BATCH)),
    HTTP("HTTP", ".json", API, false, null, List.of(BATCH)),

    SEATUNNEL("SeaTunnel", ".conf", DATA_INTEGRATION, false, null, List.of(BATCH)),

    CONDITION("Condition", "", CONTROL, false, null, List.of(BATCH)),
    SUB_WORKFLOW("Sub WorkflowDefinition", "", CONTROL, false, null, List.of(BATCH)),
    SWITCH("Switch", "", CONTROL, false, null, List.of(BATCH)),
    DEPENDENT("Dependent", "", CONTROL, false, null, List.of(BATCH));

    private final String label;
    private final String ext;
    private final TaskCategory category;
    private final boolean needsDatasource;
    private final String datasourceType;
    private final List<ExecutionMode> executionModes;

    public boolean isControlFlow() {
        return category == CONTROL;
    }

    public boolean isSql() {
        return category == SQL;
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static TaskType fromJson(String value) {
        return value == null ? null : TaskType.valueOf(value);
    }
}
