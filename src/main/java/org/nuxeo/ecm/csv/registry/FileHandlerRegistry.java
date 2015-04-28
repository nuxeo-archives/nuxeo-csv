/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Munch
 */

package org.nuxeo.ecm.csv.registry;

import java.util.ArrayList;
import java.util.Collection;

import org.nuxeo.ecm.csv.descriptor.CSVFileHandlerDescriptor;
import org.nuxeo.runtime.model.SimpleContributionRegistry;

/**
 * CSVFileHandlerDescriptor registry handling merge.
 * 
 * @since 7.3
 */
public class FileHandlerRegistry extends SimpleContributionRegistry<CSVFileHandlerDescriptor> {
    public FileHandlerRegistry() {
        super();
    }

    @Override
    public String getContributionId(CSVFileHandlerDescriptor descriptor) {
        return descriptor.getId();
    }

    @Override
    public boolean isSupportingMerge() {
        return true;
    }

    @Override
    public CSVFileHandlerDescriptor clone(CSVFileHandlerDescriptor orig) {
        return orig.clone();
    }

    @Override
    public void merge(CSVFileHandlerDescriptor src, CSVFileHandlerDescriptor dst) {
        dst.merge(src);
    }

    public Collection<CSVFileHandlerDescriptor> getFileHandlers() {
        Collection<CSVFileHandlerDescriptor> fileHandlers = new ArrayList<>();

        for (CSVFileHandlerDescriptor contrib : currentContribs.values()) {
            if (Boolean.TRUE.equals(contrib.getEnabled())) {
                fileHandlers.add(contrib);
            }
        }

        return fileHandlers;
    }
}
