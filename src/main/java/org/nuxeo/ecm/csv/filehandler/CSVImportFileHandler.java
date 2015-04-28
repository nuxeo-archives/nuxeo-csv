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

package org.nuxeo.ecm.csv.filehandler;

import java.io.File;

import org.nuxeo.ecm.core.api.ClientException;

/**
 * File handler which will prepare an uploaded file for the import.
 * 
 * @since 7.3
 */
public interface CSVImportFileHandler {
    /**
     * Check if the current file handler accepts the specified uploaded file.
     * 
     * @param file
     *            Uploaded file copied on the file system.
     * @param name
     *            Name of the uploaded file.
     * @param contentType
     *            Content type of the uploaded file.
     * @return true if this file handler accepts the uploaded file, false otherwise.
     */
    boolean accept(File file, String name, String contentType);

    /**
     * Handles the specified file.
     * 
     * @param file
     *            Uploaded file.
     * @return CSV file to import.
     * @throws ClientException
     */
    File handle(File file) throws ClientException;
}
