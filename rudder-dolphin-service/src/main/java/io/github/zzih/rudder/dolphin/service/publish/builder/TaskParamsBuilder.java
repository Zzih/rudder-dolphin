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

package io.github.zzih.rudder.dolphin.service.publish.builder;

import io.github.zzih.rudder.dolphin.client.model.TaskType;

import java.util.Map;

import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;

/**
 * Per-task-type adapter from Rudder {@code scriptContent} (parsed as a Map) into the
 * {@link AbstractParameters} subclass DolphinScheduler ships for that task type.
 *
 * <p>Returning a typed DS {@link AbstractParameters} (instead of a raw {@link Map}) gives us
 * compile-time field validation, IDE navigation, and a clear upgrade signal whenever DS evolves
 * its parameter shape.
 */
public interface TaskParamsBuilder {

    /** Whether this builder handles the given Rudder task type. */
    boolean supports(TaskType type);

    /** Convert the parsed scriptContent map into the DS parameters object. */
    AbstractParameters build(Map<String, Object> source, TaskType type);
}
