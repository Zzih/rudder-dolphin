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

import io.github.zzih.rudder.dolphin.client.model.ResourceBundle;
import io.github.zzih.rudder.dolphin.common.exception.BizException;
import io.github.zzih.rudder.dolphin.service.client.DolphinSchedulerClient;
import io.github.zzih.rudder.dolphin.service.enums.PublishErrorCode;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Push {@link ResourceBundle} bytes into DolphinScheduler's resource center.
 *
 * <p>DS partitions the resource center per tenant, so legal {@code currentDir} values must start
 * with the tenant root (e.g. {@code /dolphinscheduler/<tenant>/resources}). Each call:
 * <ol>
 *   <li>Resolves the tenant root via {@code GET /resources/base-dir} (uses the publishing user's token).</li>
 *   <li>Computes the absolute base = {@code <tenantRoot>/<subDir>} (configured top-level prefix,
 *       defaults to {@code rudder}).</li>
 *   <li>Mirrors the rudder-side relative path under that base — a bundle with
 *       {@code path=demo-jar/spark/foo.jar} lands at {@code <base>/demo-jar/spark/foo.jar},
 *       intermediate dirs created on demand and deduped per call.</li>
 *   <li>For each resource: best-effort delete (DS rejects same-fullName creates) + upload, and
 *       record {@code rudder path → DS fullName} into {@link ResourceResolver} so jar-task builders
 *       can translate {@code jarPath}.</li>
 * </ol>
 *
 * <p>SHA-256 is verified per resource; any mismatch fails the whole publish (catches network /
 * serialisation corruption rather than letting bad bytes land silently on DS).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceSynchronizer {

    private static final String FILE_TYPE = "FILE";

    private final DolphinSchedulerClient dolphinSchedulerClient;
    private final ResourceResolver resourceResolver;

    /**
     * Top-level subdirectory under the tenant root. Empty / {@code "/"} means "no extra prefix —
     * write directly under the tenant root". Leading and trailing slashes are stripped, so
     * {@code rudder}, {@code /rudder} and {@code /rudder/} all work.
     */
    @Value("${rudder-dolphin.dolphinscheduler.resource-base-dir:rudder}")
    private String subDirConfig;

    public Summary upsertAll(List<ResourceBundle> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return new Summary(0, 0);
        }

        String tenantRoot = dolphinSchedulerClient.queryResourceBaseDir(FILE_TYPE);
        String subDir = normalize(subDirConfig);
        String baseDir = subDir.isEmpty() ? tenantRoot : tenantRoot + "/" + subDir;
        Set<String> ensuredDirs = new HashSet<>();
        if (!subDir.isEmpty()) {
            ensureDirectoryPath(tenantRoot, subDir, ensuredDirs);
        }
        log.info("DS resource baseDir resolved: tenantRoot={}, sub={}, base={}",
                tenantRoot, subDir, baseDir);

        int uploaded = 0;
        int skipped = 0;
        for (ResourceBundle rb : incoming) {
            try {
                if (upsertOne(rb, baseDir, ensuredDirs)) {
                    uploaded++;
                } else {
                    skipped++;
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to sync resource: path={}, name={}", rb.getPath(), rb.getName(), e);
                throw new BizException(PublishErrorCode.DS_API_ERROR,
                        "Failed to upload resource '" + rb.getName() + "': " + e.getMessage());
            }
        }
        return new Summary(uploaded, skipped);
    }

    /** @return true if uploaded; false if skipped (currently always true — kept for future dedup). */
    private boolean upsertOne(ResourceBundle rb, String baseDir, Set<String> ensuredDirs) {
        if (rb.getContent() == null || rb.getContent().length == 0) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "ResourceBundle '" + rb.getName() + "' content is empty");
        }
        verifySha256(rb);

        String relativeDir = parentOfRelativePath(rb.getPath());
        String parentDir = relativeDir.isEmpty() ? baseDir : baseDir + "/" + relativeDir;
        String fullName = parentDir + "/" + rb.getName();

        if (!relativeDir.isEmpty()) {
            ensureDirectoryPath(baseDir, relativeDir, ensuredDirs);
        }

        // DS rejects same-fullName creates; best-effort delete first, swallow not-found.
        try {
            dolphinSchedulerClient.deleteResource(fullName);
        } catch (Exception e) {
            log.debug("Delete before upload returned non-fatal: fullName={}, err={}", fullName, e.getMessage());
        }

        log.info("Uploading resource to DS: name={}, currentDir={}, size={}",
                rb.getName(), parentDir, rb.getContent().length);
        dolphinSchedulerClient.createResourceFile(rb.getName(), parentDir, rb.getContent());
        resourceResolver.put(rb.getPath(), fullName);
        return true;
    }

    private void verifySha256(ResourceBundle rb) {
        if (rb.getSha256() == null || rb.getSha256().isBlank()) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "ResourceBundle '" + rb.getName() + "' missing sha256");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rb.getContent());
            String actual = HexFormat.of().formatHex(digest);
            if (!actual.equalsIgnoreCase(rb.getSha256())) {
                throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                        "Resource '" + rb.getName() + "' sha256 mismatch: expected="
                                + rb.getSha256() + ", actual=" + actual);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(PublishErrorCode.PUBLISH_FAILED,
                    "Failed to verify sha256 for '" + rb.getName() + "': " + e.getMessage());
        }
    }

    /**
     * Walk {@code relativeDir} segment by segment under {@code baseDir} and create any missing
     * directory levels. Already-attempted full paths are tracked in {@code ensuredDirs} so
     * resources sharing a common prefix don't re-issue the same DS calls.
     */
    private void ensureDirectoryPath(String baseDir, String relativeDir, Set<String> ensuredDirs) {
        String currentParent = baseDir;
        for (String seg : relativeDir.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            String fullDir = currentParent + "/" + seg;
            if (ensuredDirs.add(fullDir)) {
                try {
                    dolphinSchedulerClient.createResourceDirectory(seg, currentParent);
                } catch (Exception e) {
                    log.debug("DS dir already exists or create failed (non-fatal): {}, err={}",
                            fullDir, e.getMessage());
                }
            }
            currentParent = fullDir;
        }
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("^/+|/+$", "");
    }

    /** {@code "demo-jar/spark/foo.jar"} → {@code "demo-jar/spark"}; bare filename → {@code ""}. */
    private static String parentOfRelativePath(String relativePath) {
        String norm = normalize(relativePath);
        int slash = norm.lastIndexOf('/');
        return slash < 0 ? "" : norm.substring(0, slash);
    }

    public record Summary(int uploaded, int skipped) {
    }
}
