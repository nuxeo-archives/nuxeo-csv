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

package org.nuxeo.ecm.csv.descriptor;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.csv.filehandler.CSVImportFileHandler;

/**
 * 
 * 
 * @since 7.3
 */
@XObject("fileHandler")
public class CSVFileHandlerDescriptor {
    @XNode("@id")
    private String id;

    @XNode("@class")
    private Class<CSVImportFileHandler> clazz;

    @XNode("@enabled")
    private Boolean enabled = Boolean.TRUE;

    @XNode("@order")
    private Integer order;

    public CSVFileHandlerDescriptor() {
        super();
    }

    public String getId() {
        return id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public CSVImportFileHandler getFileHandler() {
        if (clazz == null) {
            throw new IllegalArgumentException("Attribute class cannot be null");
        }

        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ClientException("Failed to instantiate class " + clazz.getName(), e);
        }
    }

    public Integer getOrder() {
        return order;
    }

    @Override
    public CSVFileHandlerDescriptor clone() {
        CSVFileHandlerDescriptor clone = new CSVFileHandlerDescriptor();

        clone.id = id;
        clone.clazz = clazz;
        clone.enabled = enabled;
        clone.order = order;

        return clone;
    }

    public void merge(CSVFileHandlerDescriptor other) {
        if (other.id != null) {
            id = other.id;
        }
        if (other.clazz != null) {
            clazz = other.clazz;
        }
        if (other.enabled != null) {
            enabled = other.enabled;
        }
        if (other.order != null) {
            order = other.order;
        }
    }
}
