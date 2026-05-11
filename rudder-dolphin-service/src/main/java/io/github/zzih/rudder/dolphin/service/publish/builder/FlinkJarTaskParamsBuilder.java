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
import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;
import io.github.zzih.rudder.dolphin.service.env.ResourceResolver;

import java.util.List;
import java.util.Map;

import org.apache.dolphinscheduler.plugin.task.api.model.ResourceInfo;
import org.apache.dolphinscheduler.plugin.task.flink.FlinkDeployMode;
import org.apache.dolphinscheduler.plugin.task.flink.FlinkParameters;
import org.apache.dolphinscheduler.plugin.task.flink.ProgramType;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 构建 DS {@link FlinkParameters}。Rudder 字段映射同 SparkJar:
 * {@code jarPath} → 通过 {@link ResourceResolver} 翻成 DS fullName 后包成 mainJar/resourceList,
 * 并补 {@code programType=JAVA}。
 */
@Component
@RequiredArgsConstructor
public class FlinkJarTaskParamsBuilder implements TaskParamsBuilder {

    private final ResourceResolver resourceResolver;

    @Override
    public boolean supports(TaskType type) {
        return type == TaskType.FLINK_JAR;
    }

    @Override
    public FlinkParameters build(Map<String, Object> source, TaskType type) {
        String mainClass = asString(source.get("mainClass"));
        String jarPath = asString(source.get("jarPath"));
        if (jarPath == null || jarPath.isBlank()) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "FLINK_JAR task missing jarPath");
        }
        if (mainClass == null || mainClass.isBlank()) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "FLINK_JAR task missing mainClass");
        }

        String dsFullName = resourceResolver.resolve(jarPath);
        if (dsFullName == null) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "FLINK_JAR task jarPath '" + jarPath
                            + "' not found in bundle.resources — rudder must include the jar bytes");
        }

        ResourceInfo mainJar = new ResourceInfo();
        mainJar.setResourceName(dsFullName);
        mainJar.setRes(dsFullName);

        FlinkParameters params = new FlinkParameters();
        params.setProgramType(ProgramType.JAVA);
        params.setMainClass(mainClass);
        params.setMainJar(mainJar);
        params.setResourceList(List.of(mainJar));
        params.setMainArgs(asString(source.get("args")));
        params.setAppName(asString(source.get("appName")));
        params.setYarnQueue(asString(source.get("queue")));
        params.setOthers(asString(source.get("others")));
        params.setFlinkVersion(asString(source.get("flinkVersion")));
        String deployMode = asString(source.get("deployMode"));
        if (deployMode != null && !deployMode.isBlank()) {
            params.setDeployMode(FlinkDeployMode.valueOf(deployMode.toUpperCase()));
        }
        return params;
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
