/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.thumbnails.internal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import javax.jcr.query.Query;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.thumbnails.Transformation;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { Runnable.class, TransformationCache.class, EventHandler.class }, property = {
        EventConstants.EVENT_TOPIC + "=org/apache/sling/api/resource/Resource/CHANGED",
        EventConstants.EVENT_FILTER + "=(&(resourceType=sling/thumbnails/transformation))",
        "scheduler.period=L3600" })
public class TransformationCache implements EventHandler, Runnable {

    private static final Logger log = LoggerFactory.getLogger(TransformationCache.class);
    private final TransformationServiceUser transformationServiceUser;
    private final Map<String, Optional<String>> cache = new HashMap<>();

    @Activate
    public TransformationCache(@Reference TransformationServiceUser transformationServiceUser) {
        this.transformationServiceUser = transformationServiceUser;
    }

    public Optional<Transformation> getTransformation(ResourceResolver resolver, String name) {
        return cache.computeIfAbsent(name, this::findTransformation).map(resolver::getResource)
                .map(r -> r.adaptTo(Transformation.class));
    }

    @Override
    public void handleEvent(Event event) {
        cache.clear();
    }

    private Optional<String> findTransformation(String name) {
        try {
            try (ResourceResolver serviceResolver = transformationServiceUser.getTransformationServiceUser()) {
                name = name.substring(1).replace("'", "''");
                log.debug("Finding transformations with {}", name);
                Iterator<Resource> transformations = serviceResolver.findResources(
                        "SELECT * FROM [nt:unstructured] WHERE (ISDESCENDANTNODE([/conf]) OR ISDESCENDANTNODE([/libs/conf]) OR ISDESCENDANTNODE([/apps/conf])) AND [sling:resourceType]='sling/thumbnails/transformation' AND [name]='"
                                + name + "'",
                        Query.JCR_SQL2);
                if (transformations.hasNext()) {
                    Resource transformation = transformations.next();
                    log.debug("Found transformation resource: {}", transformation);
                    return Optional.of(transformation.getPath());
                }
                return Optional.empty();
            }
        } catch (LoginException le) {
            throw new RuntimeException("Could not get service resolver", le);
        }
    }

    public Set<Entry<String,Optional<String>>> getCacheEntries(){
        return cache.entrySet();
    }

    @Override
    public void run() {
        cache.clear();
    }
}
