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

package io.github.zzih.rudder.dolphin.service.env;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 维护 Rudder {@code ResourceBundle.path} → DS 资源中心 {@code fullName} 的索引,
 * 供 {@code SparkJarTaskParamsBuilder} / {@code FlinkJarTaskParamsBuilder} 把任务里的 jarPath
 * 翻译成 DS 资源中心可识别的 fullName。
 *
 * <p>映射在 {@link ResourceSynchronizer} 上传成功后写入。每次发布前由 sync 流程刷新。
 */
@Slf4j
@Component
public class ResourceResolver {

    private final Map<String, String> pathToFullName = new ConcurrentHashMap<>();

    public String resolve(String rudderPath) {
        return pathToFullName.get(rudderPath);
    }

    public void put(String rudderPath, String dsFullName) {
        if (rudderPath != null && dsFullName != null) {
            pathToFullName.put(rudderPath, dsFullName);
        }
    }
}
