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

package io.github.zzih.rudder.dolphin.service.task.builder;

import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * rudder SqlTaskParams/SparkJarTaskParams → DS SparkParameters
 * <p>
 * Handles: SPARK_SQL (subType=SPARK_SQL), SPARK_JAR (subType=SPARK_JAR)
 */
@Component
public class SparkTaskParamsBuilder implements TaskParamsBuilder {

    private static final int DEFAULT_DRIVER_CORES = 1;
    private static final String DEFAULT_DRIVER_MEMORY = "2g";
    private static final int DEFAULT_NUM_EXECUTORS = 2;
    private static final int DEFAULT_EXECUTOR_CORES = 1;
    private static final String DEFAULT_EXECUTOR_MEMORY = "2g";

    @Override
    public Map<String, Object> build(Map<String, Object> taskParams, String subType) {
        return "SPARK_JAR".equals(subType) ? buildJar(taskParams) : buildSql(taskParams);
    }

    private Map<String, Object> buildSql(Map<String, Object> taskParams) {
        String sql = (String) taskParams.get("sql");
        if (sql == null || sql.isBlank()) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Spark SQL task 'sql' is required");
        }

        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("programType", "SQL");
        ds.put("rawScript", sql);
        ds.put("sqlExecutionType", "SCRIPT");
        applyResourceDefaults(ds, taskParams);
        ds.put("mainClass", "");
        ds.put("mainJar", null);
        ds.put("mainArgs", "");
        ds.put("appName", "");
        ds.put("others", resolveOthers(taskParams));
        ds.put("resourceList", new ArrayList<>());
        ds.put("localParams", taskParams.getOrDefault("localParams", new ArrayList<>()));
        return ds;
    }

    private Map<String, Object> buildJar(Map<String, Object> taskParams) {
        String mainClass = (String) taskParams.get("mainClass");
        String jarPath = (String) taskParams.get("jarPath");
        if (mainClass == null || mainClass.isBlank()) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Spark JAR task 'mainClass' is required");
        }
        if (jarPath == null || jarPath.isBlank()) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Spark JAR task 'jarPath' is required");
        }

        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("programType", "JAVA");
        ds.put("mainClass", mainClass);
        ds.put("mainJar", Map.of("resourceName", jarPath));
        ds.put("mainArgs", taskParams.getOrDefault("args", ""));
        ds.put("deployMode", taskParams.getOrDefault("deployMode", "cluster"));
        ds.put("appName", taskParams.getOrDefault("appName", ""));
        applyResourceDefaults(ds, taskParams);
        ds.put("others", resolveOthers(taskParams));
        ds.put("rawScript", "");
        ds.put("resourceList", new ArrayList<>());
        ds.put("localParams", taskParams.getOrDefault("localParams", new ArrayList<>()));
        return ds;
    }

    @SuppressWarnings("unchecked")
    private void applyResourceDefaults(Map<String, Object> ds, Map<String, Object> taskParams) {
        Map<String, Object> resource = (Map<String, Object>) taskParams.get("resource");
        ds.put("deployMode", taskParams.getOrDefault("deployMode", "client"));
        ds.put("master", taskParams.getOrDefault("master", "yarn"));
        ds.put("driverCores", getResourceInt(resource, "driverCores", DEFAULT_DRIVER_CORES));
        ds.put("driverMemory", getResourceMemory(resource, "driverMemory", DEFAULT_DRIVER_MEMORY));
        ds.put("numExecutors", getResourceInt(resource, "numExecutors", DEFAULT_NUM_EXECUTORS));
        ds.put("executorCores", getResourceInt(resource, "executorCores", DEFAULT_EXECUTOR_CORES));
        ds.put("executorMemory", getResourceMemory(resource, "executorMemory", DEFAULT_EXECUTOR_MEMORY));
        if (taskParams.containsKey("queue")) {
            ds.put("yarnQueue", taskParams.get("queue"));
        }
    }

    private int getResourceInt(Map<String, Object> resource, String key, int defaultVal) {
        if (resource == null) {
            return defaultVal;
        }
        Object val = resource.get(key);
        return val instanceof Number n ? n.intValue() : defaultVal;
    }

    private String getResourceMemory(Map<String, Object> resource, String key, String defaultVal) {
        if (resource == null) {
            return defaultVal;
        }
        Object val = resource.get(key);
        if (val instanceof String s) {
            return s;
        }
        if (val instanceof Number n) {
            return n.intValue() + "g";
        }
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    private String resolveOthers(Map<String, Object> taskParams) {
        Map<String, String> engineParams = (Map<String, String>) taskParams.get("engineParams");
        if (engineParams == null || engineParams.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        engineParams.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("--conf ").append(k).append("=").append(v);
        });
        return sb.toString();
    }
}
