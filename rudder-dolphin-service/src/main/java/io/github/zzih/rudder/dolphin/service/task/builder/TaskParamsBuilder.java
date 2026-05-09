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

import java.util.Map;

/**
 * Converts rudder task params (Map) to DolphinScheduler-compatible task params (Map).
 * Each implementation handles one or more task types.
 */
public interface TaskParamsBuilder {

    /**
     * Build DS-compatible task params from the incoming rudder params.
     *
     * @param taskParams incoming task params from the caller
     * @param subType    sub-type hint from RudderDolphinTaskType (e.g., datasource type, SPARK_SQL vs SPARK_JAR)
     * @return DS-compatible task params map, will be serialized to JSON string
     */
    Map<String, Object> build(Map<String, Object> taskParams, String subType);
}
