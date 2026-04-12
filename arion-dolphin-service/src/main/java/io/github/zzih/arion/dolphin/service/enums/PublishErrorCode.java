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

package io.github.zzih.arion.dolphin.service.enums;

import io.github.zzih.arion.dolphin.common.result.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PublishErrorCode implements ErrorCode {

    DS_API_ERROR(10001, "DolphinScheduler API call failed"),
    PROJECT_NOT_FOUND(10002, "Project not found in DolphinScheduler"),
    WORKFLOW_NOT_FOUND(10003, "Workflow not found in DolphinScheduler"),
    PUBLISH_FAILED(10004, "Publish failed"),
    INVALID_TASK_DEFINITION(10005, "Invalid task definition JSON"),
    INVALID_TASK_RELATION(10006, "Invalid task relation JSON");

    private final int code;
    private final String message;
}
