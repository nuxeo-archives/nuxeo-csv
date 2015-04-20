/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.csv.CSVImporterWork;
import org.nuxeo.runtime.api.Framework;
import org.richfaces.model.UploadedFile;

/**
 * Handles ZIP files to prepare the import.
 * <p>
 * The ZIP file will be extracted in nuxeo.csv.blobs.folder, everything in this folder will be deleted first.
 * <p>
 * The preparation will if fail if another import is already running.
 * <p>
 * The ZIP file must contain a CSV file at the root, the first one found will be used for the import. If there is no CSV
 * file, this file handler falls back to the default behaviour and returns the uploaded ZIP file.
 * 
 * @since 7.3
 */
public class CSVImportZipHandler implements CSVImportFileHandler {
    public CSVImportZipHandler() {
        super();
    }

    @Override
    public boolean accept(UploadedFile uploadedFile, File file) {
        if (uploadedFile == null) {
            return false;
        }

        String csvFileName = FilenameUtils.getName(uploadedFile.getName());

        return "application/zip".equals(uploadedFile.getContentType()) || StringUtils.endsWithIgnoreCase(csvFileName, ".zip");
    }

    @Override
    public File handle(File zipFile) throws ClientException {
        if (zipFile == null) {
            throw new IllegalArgumentException("zipFile cannot be null");
        }

        String csvFolderPath = Framework.getProperty(CSVImporterWork.NUXEO_CSV_BLOBS_FOLDER);
        if (StringUtils.isBlank(csvFolderPath)) {
            throw new ClientException("The property " + CSVImporterWork.NUXEO_CSV_BLOBS_FOLDER + " is not set");
        }

        File csvFolder = new File(csvFolderPath);
        if (!csvFolder.exists()) {
            csvFolder.mkdirs();
        }
        if (!csvFolder.exists() || !csvFolder.isDirectory()) {
            throw new ClientException("Cannot create directory " + csvFolder.getPath());
        }

        try {
            // cleaning of the csv blob folder
            cleanBlobsFolder(csvFolder);

            // unzip in the csv blob folder
            unzip(zipFile, csvFolder);

            // csvFile is now the first CSV file in the csv blob folder
            File csvFile = findCSVFile(csvFolder);
            if (csvFile != null) {
                return csvFile;
            }

            return zipFile;
        } catch (IOException e) {
            throw new ClientException("Failed to extract ZIP file " + zipFile.getPath(), e);
        }
    }

    protected void unzip(File zipFile, File csvFolder) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File file = new File(csvFolder, entry.getName());
                file.getParentFile().mkdirs();
                if (!entry.isDirectory()) {
                    try (InputStream inputStream = new BufferedInputStream(zip.getInputStream(entry))) {
                        FileUtils.copyInputStreamToFile(inputStream, file);
                    }
                }
            }
        }
    }

    protected File findCSVFile(File csvFolder) {
        File[] files = csvFolder.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                if (StringUtils.endsWithIgnoreCase(file.getName(), ".csv")) {
                    return file;
                }
            }
        }

        return null;
    }

    protected void cleanBlobsFolder(File csvFolder) throws IOException {
        WorkManager workManager = Framework.getService(WorkManager.class);
        String csvQueueId = workManager.getCategoryQueueId(CSVImporterWork.CATEGORY_CSV_IMPORTER);
        int csvQueueSize = workManager.getQueueSize(csvQueueId, null);
        if (csvQueueSize > 0) {
            throw new ClientException("Cannot clean the CSV blobs folder, " + csvQueueSize + " work(s) already running or scheduled");
        }

        FileUtils.cleanDirectory(csvFolder);
    }
}
