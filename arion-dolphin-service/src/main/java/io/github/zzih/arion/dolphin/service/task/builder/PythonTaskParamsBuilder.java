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
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * rudder ScriptTaskParams → DS PythonParameters
 * <p>
 * rudder: { content: "print('hello')" }
 * DS:     { rawScript: "print('hello')", resourceList: [], localParams: [] }
 */
@Component
public class PythonTaskParamsBuilder implements TaskParamsBuilder {

    @Override
    public Map<String, Object> build(Map<String, Object> taskParams, String subType) {
        String content = (String) taskParams.get("content");
        if (content == null || content.isBlank()) {
            throw new BizException(PublishErrorCode.INVALID_TASK_DEFINITION,
                    "Python task 'content' is required");
        }

        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("rawScript", content);
        ds.put("resourceList", taskParams.getOrDefault("resourceList", new ArrayList<>()));
        ds.put("localParams", taskParams.getOrDefault("localParams", new ArrayList<>()));
        return ds;
    }
}
