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

package io.github.zzih.rudder.dolphin.common.constants;

public final class PublishConstants {

    private PublishConstants() {
    }

    public static final String PROJECT_BUNDLE = "PROJECT_BUNDLE";
    public static final String PROJECT_NAME = "PROJECT_NAME";
    public static final String PROJECT_CODE = "PROJECT_CODE";
    /** {@code Map<String, Long>}: workflow name → DS code, scoped to the current publish project. */
    public static final String PROJECT_WORKFLOW_NAME_MAP = "PROJECT_WORKFLOW_NAME_MAP";

    public static final String IS_NEW_PROJECT = "IS_NEW_PROJECT";
    public static final String IS_FULL_PUBLISH = "IS_FULL_PUBLISH";

    public static final String OLD_PROJECT = "OLD_PROJECT";
    public static final String OLD_WORKFLOW_LIST = "OLD_WORKFLOW_LIST";
    public static final String OLD_DAG_DATA_MAP = "OLD_DAG_DATA_MAP";

    public static final String WORKFLOW_ADD_LIST = "WORKFLOW_ADD_LIST";
    public static final String WORKFLOW_UPDATE_LIST = "WORKFLOW_UPDATE_LIST";
    public static final String WORKFLOW_BUNDLE_MAP = "WORKFLOW_BUNDLE_MAP";

    public static final String OFFLINE_WORKFLOW_CODES = "OFFLINE_WORKFLOW_CODES";
    public static final String CREATED_WORKFLOW_CODES = "CREATED_WORKFLOW_CODES";
    public static final String ONLINED_WORKFLOW_CODES = "ONLINED_WORKFLOW_CODES";
    public static final String CREATED_SCHEDULE_IDS = "CREATED_SCHEDULE_IDS";
    public static final String UPDATED_OLD_SCHEDULES = "UPDATED_OLD_SCHEDULES";
    public static final String UPDATE_CREATED_SCHEDULE_IDS = "UPDATE_CREATED_SCHEDULE_IDS";

    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";

    public static final String INTERRUPT = "INTERRUPT";

    // PublishResult accumulation slots — handlers append outcomes here, strategy aggregates at the end.
    public static final String PROJECT_OUTCOME = "PROJECT_OUTCOME";
    public static final String WORKFLOW_OUTCOMES = "WORKFLOW_OUTCOMES";
    public static final String DATASOURCES_CREATED = "DATASOURCES_CREATED";
    public static final String DATASOURCES_UPDATED = "DATASOURCES_UPDATED";
    public static final String DATASOURCES_SKIPPED = "DATASOURCES_SKIPPED";
    public static final String RESOURCES_UPLOADED = "RESOURCES_UPLOADED";
    public static final String RESOURCES_SKIPPED = "RESOURCES_SKIPPED";
    public static final String SCHEDULES_UPDATED = "SCHEDULES_UPDATED";
}
