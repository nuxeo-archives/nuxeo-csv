/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Thomas Roger
 */

package org.nuxeo.ecm.csv;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.Work.State;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.csv.descriptor.AcceptedTypeDescriptor;
import org.nuxeo.ecm.csv.descriptor.CSVFileHandlerDescriptor;
import org.nuxeo.ecm.csv.descriptor.CSVFileHandlerDescriptorComparator;
import org.nuxeo.ecm.csv.registry.AcceptedTypeRegistry;
import org.nuxeo.ecm.csv.registry.FileHandlerRegistry;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * @since 5.7
 */
public class CSVImporterImpl extends DefaultComponent implements CSVImporter {
    private static final Log LOG = LogFactory.getLog(CSVImporterImpl.class);

    private static final String FILE_HANDLERS_EP = "fileHandlers";

    private static final String ACCEPTED_TYPES_EP = "acceptedTypes";

    private FileHandlerRegistry fileHandlerRegistry;

    private AcceptedTypeRegistry acceptedTypeRegistry;

    public CSVImporterImpl() {
        super();
    }

    @Override
    public void activate(ComponentContext context) {
        fileHandlerRegistry = new FileHandlerRegistry();
        acceptedTypeRegistry = new AcceptedTypeRegistry();
    }

    @Override
    public void deactivate(ComponentContext context) {
        fileHandlerRegistry = null;
        acceptedTypeRegistry = null;
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (FILE_HANDLERS_EP.equals(extensionPoint) && contribution instanceof CSVFileHandlerDescriptor) {
            CSVFileHandlerDescriptor descriptor = (CSVFileHandlerDescriptor) contribution;
            fileHandlerRegistry.addContribution(descriptor);
        } else if (ACCEPTED_TYPES_EP.equals(extensionPoint) && contribution instanceof AcceptedTypeDescriptor) {
            AcceptedTypeDescriptor descriptor = (AcceptedTypeDescriptor) contribution;
            acceptedTypeRegistry.addContribution(descriptor);
        } else {
            LOG.warn("Unknown extension point " + extensionPoint);
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (FILE_HANDLERS_EP.equals(extensionPoint) && contribution instanceof CSVFileHandlerDescriptor) {
            CSVFileHandlerDescriptor descriptor = (CSVFileHandlerDescriptor) contribution;
            fileHandlerRegistry.removeContribution(descriptor);
        } else if (ACCEPTED_TYPES_EP.equals(extensionPoint) && contribution instanceof AcceptedTypeDescriptor) {
            AcceptedTypeDescriptor descriptor = (AcceptedTypeDescriptor) contribution;
            acceptedTypeRegistry.removeContribution(descriptor);
        } else {
            LOG.warn("Unknown extension point " + extensionPoint);
        }
    }

    @Override
    public String launchImport(CoreSession session, String parentPath, File csvFile, String csvFileName,
            CSVImporterOptions options) {
        CSVImporterWork work = new CSVImporterWork(session.getRepositoryName(), parentPath,
                session.getPrincipal().getName(), csvFile, csvFileName, options);
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        workManager.schedule(work, WorkManager.Scheduling.IF_NOT_RUNNING_OR_SCHEDULED);
        return work.getId();
    }

    @Override
    public CSVImportStatus getImportStatus(String id) {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        State state = workManager.getWorkState(id);
        if (state == null) {
            return null;
        } else if (state == State.COMPLETED) {
            return new CSVImportStatus(CSVImportStatus.State.COMPLETED);
        } else if (state == State.SCHEDULED) {
            String queueId = workManager.getCategoryQueueId(CSVImporterWork.CATEGORY_CSV_IMPORTER);
            int queueSize = workManager.getQueueSize(queueId, State.SCHEDULED);
            return new CSVImportStatus(CSVImportStatus.State.SCHEDULED, 0, queueSize);
        } else { // RUNNING
            return new CSVImportStatus(CSVImportStatus.State.RUNNING);
        }
    }

    @Override
    public List<CSVImportLog> getImportLogs(String id) {
        return getLastImportLogs(id, -1);
    }

    @Override
    public List<CSVImportLog> getImportLogs(String id, CSVImportLog.Status... status) {
        return getLastImportLogs(id, -1, status);
    }

    @Override
    public List<CSVImportLog> getLastImportLogs(String id, int max) {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        Work work = workManager.find(id, null);
        if (work == null) {
            work = workManager.find(id, State.COMPLETED);
            if (work == null) {
                return Collections.emptyList();
            }
        }
        List<CSVImportLog> importLogs = ((CSVImporterWork) work).getImportLogs();
        max = (max == -1 || max > importLogs.size()) ? importLogs.size() : max;
        return importLogs.subList(importLogs.size() - max, importLogs.size());
    }

    @Override
    public List<CSVImportLog> getLastImportLogs(String id, int max, CSVImportLog.Status... status) {
        List<CSVImportLog> importLogs = getLastImportLogs(id, max);
        return status.length == 0 ? importLogs : filterImportLogs(importLogs, status);
    }

    protected List<CSVImportLog> filterImportLogs(List<CSVImportLog> importLogs, CSVImportLog.Status... status) {
        List<CSVImportLog.Status> statusList = Arrays.asList(status);
        List<CSVImportLog> filteredLogs = new ArrayList<CSVImportLog>();
        for (CSVImportLog log : importLogs) {
            if (statusList.contains(log.getStatus())) {
                filteredLogs.add(log);
            }
        }
        return filteredLogs;
    }

    @Override
    public CSVImportResult getImportResult(String id) {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        Work work = workManager.find(id, State.COMPLETED);
        if (work == null) {
            return null;
        }

        List<CSVImportLog> importLogs = ((CSVImporterWork) work).getImportLogs();
        return CSVImportResult.fromImportLogs(importLogs);
    }

    @Override
    public Set<String> getAcceptedTypes() {
        Set<String> acceptedTypes = new HashSet<>();
        for (AcceptedTypeDescriptor acceptedType : acceptedTypeRegistry.getAcceptedTypes()) {
            acceptedTypes.add(acceptedType.getType());
        }

        return acceptedTypes;
    }

    @Override
    public File prepareUploadedFile(File uploadedFile, String name, String contentType) throws ClientException {
        for (CSVFileHandlerDescriptor fileHandler : getFileHandlers()) {
            if (fileHandler.getFileHandler().accept(uploadedFile, name, contentType)) {
                return fileHandler.getFileHandler().handle(uploadedFile);
            }
        }

        return uploadedFile;
    }

    protected Set<CSVFileHandlerDescriptor> getFileHandlers() {
        Set<CSVFileHandlerDescriptor> sortedFileHandlers = new TreeSet<>(CSVFileHandlerDescriptorComparator.INSTANCE);
        sortedFileHandlers.addAll(fileHandlerRegistry.getFileHandlers());

        return sortedFileHandlers;
    }
}
