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

package org.nuxeo.ecm.csv;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.csv.filehandler.CSVImportZipHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * 
 * 
 * @since 7.3
 */
@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@Deploy({
    "org.nuxeo.ecm.csv",
    "org.nuxeo.runtime.datasource"
    ,"org.nuxeo.ecm.platform.types.api",
    "org.nuxeo.ecm.platform.types.core"
})
@LocalDeploy({ "org.nuxeo.ecm.csv:OSGI-INF/test-types-contrib.xml",
        "org.nuxeo.ecm.csv:OSGI-INF/test-ui-types-contrib.xml" })
public class CSVImportZipHandlerTest {
    @Inject
    private CoreSession coreSession;

    @Inject
    private CSVImporter csvImporter;

    @Inject
    private WorkManager workManager;

    public CSVImportZipHandlerTest() {
        super();
    }

    @Test
    public void test() throws IOException, InterruptedException {
        // create a temporary ZIP file
        File zipFile = File.createTempFile("testZipImport", ".zip");
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("import.zip");
        FileUtils.copyInputStreamToFile(inputStream, zipFile);

        // clean the CSV blobs folder
        File csvBlobsFolder = new File("target/csvblobsfolder");
        FileUtils.deleteQuietly(csvBlobsFolder);
        csvBlobsFolder.mkdirs();
        Framework.getProperties().setProperty(CSVImporterWork.NUXEO_CSV_BLOBS_FOLDER, csvBlobsFolder.getPath());

        // call CSVImportZipHandler
        CSVImportZipHandler zipHandler = new CSVImportZipHandler();
        File csvFile = zipHandler.handle(zipFile);
        Assert.assertEquals("target/csvblobsfolder/import.csv", csvFile.getPath());

        Collection<File> files = FileUtils.listFiles(csvBlobsFolder, FileFileFilter.FILE, TrueFileFilter.INSTANCE);
        List<File> filesList = new ArrayList<>(files);
        Collections.sort(filesList);
        Assert.assertEquals(4, filesList.size());
        Assert.assertEquals("target/csvblobsfolder/folder1/image1.jpg", filesList.get(0).getPath());
        Assert.assertEquals("target/csvblobsfolder/folder2/image2.jpg", filesList.get(1).getPath());
        Assert.assertEquals("target/csvblobsfolder/folder2/subfolder1/image3.jpg", filesList.get(2).getPath());
        Assert.assertEquals("target/csvblobsfolder/import.csv", filesList.get(3).getPath());

        // launch a CSV import
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }

        DocumentModel folder = coreSession.createDocumentModel("/", "folder", "Folder");
        folder = coreSession.createDocument(folder);
        coreSession.save();

        CSVImporterOptions options = new CSVImporterOptions.Builder().sendEmail(false).build();
        csvImporter.launchImport(coreSession, "/folder", csvFile, csvFile.getName(), options);

        coreSession.save();
        TransactionHelper.commitOrRollbackTransaction();
        Assert.assertTrue(workManager.awaitCompletion(20, TimeUnit.SECONDS));
        TransactionHelper.startTransaction();

        DocumentModelList children = coreSession.query("SELECT * FROM Document WHERE ecm:parentId = '" + folder.getId() + "' AND ecm:isVersion = 0");
        Assert.assertNotNull(children);
        Assert.assertEquals(3, children.size());
        for (DocumentModel child : children) {
            BlobHolder blobHolder = child.getAdapter(BlobHolder.class);
            Assert.assertNotNull(blobHolder);
            Blob blob = blobHolder.getBlob();
            Assert.assertNotNull(blob);
            Assert.assertTrue(blob.getLength() > 0);
            Assert.assertTrue(StringUtils.isNotBlank(blob.getFilename()));
        }
    }
}
