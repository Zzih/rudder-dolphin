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
 * rudder SqlTaskParams → DS SqlParameters
 * <p>
 * Handles: HIVE_SQL, STARROCKS_SQL, MYSQL_SQL, TRINO_SQL
 * subType carries the datasource type (e.g., "HIVE", "MYSQL")
 * <p>
 * rudder: { dataSourceId, sql, sqlType, queryLimit, preStatements, postStatements }
 * DS:     { type, datasource, sql, sqlType(int), limit, preStatements, postStatements, localParams }
 */
@Component
public class SqlTaskParamsBuilder implements TaskParamsBuilder {

    @Override
    public Map<String, Object> build(Map<String, Object> taskParams, String subType) {
        String sql = (String) taskParams.get("sql");
        if (sql == null || sql.isBlank()) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "SQL task 'sql' is required");
        }

        Object dataSourceId = taskParams.get("dataSourceId");
        if (dataSourceId == null) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "SQL task 'dataSourceId' is required");
        }

        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("type", subType);
        ds.put("datasource", ((Number) dataSourceId).intValue());
        ds.put("sql", sql);
        ds.put("sqlType", resolveSqlType(taskParams.get("sqlType")));
        ds.put("preStatements", taskParams.getOrDefault("preStatements", new ArrayList<>()));
        ds.put("postStatements", taskParams.getOrDefault("postStatements", new ArrayList<>()));
        ds.put("segmentSeparator", ";");
        ds.put("displayRows", 50);
        ds.put("limit", taskParams.getOrDefault("queryLimit", 10000));
        ds.put("localParams", taskParams.getOrDefault("localParams", new ArrayList<>()));
        return ds;
    }

    private int resolveSqlType(Object sqlType) {
        if (sqlType == null) {
            return 1; // NON_QUERY
        }
        if (sqlType instanceof Number n) {
            return n.intValue();
        }
        String s = sqlType.toString();
        return "QUERY".equalsIgnoreCase(s) ? 0 : 1;
    }
}
