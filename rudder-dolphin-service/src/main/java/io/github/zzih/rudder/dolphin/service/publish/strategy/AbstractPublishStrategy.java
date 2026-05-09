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

package io.github.zzih.rudder.dolphin.service.publish.strategy;

import io.github.zzih.rudder.dolphin.common.constants.PublishConstants;
import io.github.zzih.rudder.dolphin.common.utils.ThreadParamMapUtils;
import io.github.zzih.rudder.dolphin.service.publish.handler.PublishAfterHandler;
import io.github.zzih.rudder.dolphin.service.publish.handler.PublishBeforeHandler;
import io.github.zzih.rudder.dolphin.service.publish.handler.PublishHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPublishStrategy {

    @Resource
    private PublishBeforeHandler publishBeforeHandler;

    @Resource
    private PublishAfterHandler publishAfterHandler;

    private List<PublishHandler> handlerList;

    @PostConstruct
    public void init() {
        handlerList = new ArrayList<>();
        handlerList.add(publishBeforeHandler);
        handlerList.addAll(getMiddleHandlers());
        handlerList.add(publishAfterHandler);
    }

    protected abstract List<PublishHandler> getMiddleHandlers();

    public void publish(Object projectData, boolean fullPublish) {
        ThreadParamMapUtils.put(PublishConstants.PROJECT_DATA, projectData);
        ThreadParamMapUtils.put(PublishConstants.IS_FULL_PUBLISH, fullPublish);
        List<PublishHandler> executedHandlers = new ArrayList<>();
        try {
            for (PublishHandler handler : handlerList) {
                if (!handler.canHandle()) {
                    log.debug("Skipping handler: {}", handler.getClass().getSimpleName());
                    continue;
                }
                log.info("Executing handler: {}", handler.getClass().getSimpleName());
                executedHandlers.add(handler);
                handler.handle();
                if (handler.isInterrupt()) {
                    log.warn("Handler interrupted the chain: {}", handler.getClass().getSimpleName());
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Handler execution failed, starting rollback", e);
            rollBack(executedHandlers);
            throw e;
        } finally {
            ThreadParamMapUtils.clear();
        }
    }

    private void rollBack(List<PublishHandler> executedHandlers) {
        ListIterator<PublishHandler> it = executedHandlers.listIterator(executedHandlers.size());
        while (it.hasPrevious()) {
            PublishHandler handler = it.previous();
            try {
                log.info("Rolling back handler: {}", handler.getClass().getSimpleName());
                handler.rollBack();
            } catch (Exception e) {
                log.error("Rollback failed for handler: {}", handler.getClass().getSimpleName(), e);
            }
        }
    }
}
