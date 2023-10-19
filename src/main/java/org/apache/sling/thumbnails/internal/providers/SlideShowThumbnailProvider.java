/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.thumbnails.internal.providers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.imageio.ImageIO;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.thumbnails.OutputFileFormat;
import org.apache.sling.thumbnails.ThumbnailSupport;
import org.apache.sling.thumbnails.extension.ThumbnailProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides Thumbnails for Microsoft PPT and PPTX files.
 */
@Component(service = ThumbnailProvider.class, immediate = true)
public class SlideShowThumbnailProvider implements ThumbnailProvider {

    private static final Logger log = LoggerFactory.getLogger(SlideShowThumbnailProvider.class);

    private final DynamicClassLoaderManager classLoaderManager;
    private final ThumbnailSupport thumbnailSupport;

    @Activate
    public SlideShowThumbnailProvider(@Reference DynamicClassLoaderManager classLoaderManager,
            @Reference ThumbnailSupport thumbnailSupport) {
        this.classLoaderManager = classLoaderManager;
        this.thumbnailSupport = thumbnailSupport;
    }

    @Override
    public boolean applies(Resource resource, String metaType) {
        try {
            MimeType mt = new MimeType(metaType);
            return mt.match("application/vnd.ms-powerpoint")
                    || mt.match("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        } catch (MimeTypeParseException e) {
            log.warn("Failed to parse mime type", e);
            return false;
        }
    }

    private boolean isLegacyFormat(Resource resource) {

        try {
            MimeType mt = new MimeType(resource.getValueMap()
                    .get(thumbnailSupport.getMetaTypePropertyPath(resource.getResourceType()), String.class));
            return mt.match("application/vnd.ms-powerpoint");
        } catch (MimeTypeParseException e) {
            log.warn("Failed to parse mime type", e);
            return false;
        }
    }

    @Override
    public InputStream getThumbnail(Resource resource) throws IOException {
        if (classLoaderManager != null) {
            Thread.currentThread().setContextClassLoader(classLoaderManager.getDynamicClassLoader());
        }

        SlideShow<?, ?> ppt = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream is = resource.adaptTo(InputStream.class)) {
            if (isLegacyFormat(resource)) {
                ppt = new HSLFSlideShow(is);
            } else {
                ppt = new XMLSlideShow(is);
            }
            Dimension dim = ppt.getPageSize();
            List<? extends Slide<?, ?>> slides = ppt.getSlides();

            BufferedImage img = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = img.createGraphics();
            graphics.setPaint(Color.white);
            graphics.fill(new Rectangle2D.Float(0, 0, dim.width, dim.height));

            if (slides != null && !slides.isEmpty()) {
                slides.get(0).draw(graphics);
            }

            ImageIO.write(img, OutputFileFormat.PNG.toString(), baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } finally {
            if (ppt != null) {
                ppt.close();
            }
        }
    }

}
