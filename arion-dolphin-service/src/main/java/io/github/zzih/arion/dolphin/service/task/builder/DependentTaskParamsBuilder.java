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

package io.github.zzih.arion.dolphin.service.task.builder;

import io.github.zzih.arion.dolphin.common.exception.BizException;
import io.github.zzih.arion.dolphin.service.enums.PublishErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * rudder DependentTaskParams → DS DependentParameters
 * <p>
 * The structures are nearly identical. This builder validates and passes through
 * with correct defaults for DS.
 * <p>
 * rudder: { dependence: { dependTaskList: [...], relation, checkInterval, failurePolicy, failureWaitingTime } }
 * DS:     { dependence: { dependTaskList: [...], relation, checkInterval, failurePolicy, failureWaitingTime }, localParams: [] }
 */
@Component
public class DependentTaskParamsBuilder implements TaskParamsBuilder {

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> build(Map<String, Object> taskParams, String subType) {
        Map<String, Object> dependence = (Map<String, Object>) taskParams.get("dependence");
        if (dependence == null) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Dependent task 'dependence' is required");
        }

        List<?> dependTaskList = (List<?>) dependence.get("dependTaskList");
        if (dependTaskList == null || dependTaskList.isEmpty()) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Dependent task 'dependence.dependTaskList' is required");
        }

        Map<String, Object> dsDependence = new LinkedHashMap<>(dependence);
        dsDependence.putIfAbsent("relation", "AND");
        dsDependence.putIfAbsent("checkInterval", 10);
        dsDependence.putIfAbsent("failurePolicy", "DEPENDENT_FAILURE_FAILURE");
        dsDependence.putIfAbsent("failureWaitingTime", 1);

        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("dependence", dsDependence);
        ds.put("localParams", taskParams.getOrDefault("localParams", new ArrayList<>()));
        return ds;
    }
}
