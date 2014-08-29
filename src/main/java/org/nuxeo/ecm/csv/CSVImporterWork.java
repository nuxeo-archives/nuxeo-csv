/*
 * (C) Copyright 2012-2013 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Thomas Roger
 *     Florent Guillaume
 */
package org.nuxeo.ecm.csv;

import static org.nuxeo.ecm.csv.CSVImportLog.Status.ERROR;
import static org.nuxeo.ecm.csv.Constants.CSV_NAME_COL;
import static org.nuxeo.ecm.csv.Constants.CSV_TYPE_COL;
import static org.nuxeo.ecm.csv.Constants.TAGS_COL;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.operations.notification.MailTemplateHelper;
import org.nuxeo.ecm.automation.core.operations.notification.SendMail;
import org.nuxeo.ecm.automation.core.scripting.Expression;
import org.nuxeo.ecm.automation.core.scripting.Scripting;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.SimpleTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.IntegerType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.csv.CSVImportLog.Status;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationService;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationServiceHelper;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.ecm.platform.types.TypeManager;
import org.nuxeo.ecm.platform.ui.web.rest.api.URLPolicyService;
import org.nuxeo.ecm.platform.url.DocumentViewImpl;
import org.nuxeo.ecm.platform.url.api.DocumentView;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Work task to import form a CSV file. Because the file is read from the local
 * filesystem, this must be executed in a local queue.
 *
 * @since 5.7
 */
public class CSVImporterWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(CSVImporterWork.class);

    private static final String TEMPLATE_IMPORT_RESULT = "templates/csvImportResult.ftl";

    public static final String CATEGORY_CSV_IMPORTER = "csvImporter";

    public static final String CONTENT_FILED_TYPE_NAME = "content";

    protected String parentPath;

    protected String username;

    protected File csvFile;

    protected String csvFileName;

    protected CSVImporterOptions options;

    protected TagService tagService;

    protected transient DateFormat dateformat;

    protected Date startDate;

    protected List<CSVImportLog> importLogs = new ArrayList<CSVImportLog>();

    public CSVImporterWork(String id) {
        super(id);
    }

    public CSVImporterWork(String repositoryName, String parentPath,
            String username, File csvFile, String csvFileName,
            CSVImporterOptions options, TagService tagService) {
        super(CSVImportId.create(repositoryName, parentPath, csvFile));
        setDocument(repositoryName, null);
        this.parentPath = parentPath;
        this.username = username;
        this.csvFile = csvFile;
        this.csvFileName = csvFileName;
        this.options = options;
        this.tagService = tagService;
        startDate = new Date();
    }

    @Override
    public String getCategory() {
        return CATEGORY_CSV_IMPORTER;
    }

    @Override
    public String getTitle() {
        return String.format("CSV import in '%s'", parentPath);
    }

    public List<CSVImportLog> getImportLogs() {
        return new ArrayList<CSVImportLog>(importLogs);
    }

    @Override
    public void work() throws Exception {
        setStatus("Importing");
        initSession();
        CSVReader csvReader = null;
        try {
            csvReader = new CSVReader(new FileReader(csvFile));
            doImport(csvReader);
        } catch (IOException e) {
            logError(0, "Error while doing the import: %s",
                    "label.csv.importer.errorDuringImport", e.getMessage());
            log.debug(e, e);
        } finally {
            if (csvReader != null) {
                csvReader.close();
            }
        }

        if (options.sendEmail()) {
            setStatus("Sending email");
            sendMail();
        }
        setStatus(null);
    }

    protected void doImport(CSVReader csvReader) throws IOException {
        log.info(String.format("Importing CSV file: %s", csvFileName));

        String[] header = csvReader.readNext();
        if (header == null) {
            // empty file?
            logError(0, "No header line, empty file?",
                    "label.csv.importer.emptyFile");
            return;
        }

        // find the index for the required name and type values
        int nameIndex = -1;
        int typeIndex = -1;
        for (int col = 0; col < header.length; col++) {
            if (CSV_NAME_COL.equals(header[col])) {
                nameIndex = col;
            } else if (CSV_TYPE_COL.equals(header[col])) {
                typeIndex = col;
            }
        }
        if (nameIndex == -1 || typeIndex == -1) {
            logError(0, "Missing 'name' or 'type' column",
                    "label.csv.importer.missingNameOrTypeColumn");
            return;
        }

        try {
            int batchSize = options.getBatchSize();
            long docsCreatedCount = 0;
            long lineNumber = 0;
            for (;;) {
                lineNumber++;
                String[] line = csvReader.readNext();
                if (line == null) {
                    break; // no more line
                }

                if (line.length == 0) {
                    // empty line
                    importLogs.add(new CSVImportLog(lineNumber, Status.SKIPPED,
                            "Empty line", "label.csv.importer.emptyLine"));
                    continue;
                }

                try {
                    if (importLine(line, lineNumber, nameIndex, typeIndex,
                            header)) {
                        docsCreatedCount++;
                        if (docsCreatedCount % batchSize == 0) {
                            commitOrRollbackTransaction();
                            startTransaction();
                        }
                    }
                } catch (ClientException e) {
                    // try next line
                    Throwable unwrappedException = unwrapException(e);
                    logError(lineNumber, "Error while importing line: %s",
                            "label.csv.importer.errorImportingLine",
                            unwrappedException.getMessage());
                    log.debug(unwrappedException, unwrappedException);
                }
            }
            try {
                session.save();
            } catch (ClientException e) {
                Throwable ue = unwrapException(e);
                logError(lineNumber, "Unable to save: %s",
                        "label.csv.importer.unableToSave", ue.getMessage());
                log.debug(ue, ue);
            }
        } finally {
            commitOrRollbackTransaction();
            startTransaction();
        }
        log.info(String.format("Done importing CSV file: %s", csvFileName));
    }

    /**
     * Import a line from the CSV file.
     *
     * @return {@code true} if a document has been created or updated,
     *         {@code false} otherwise.
     */
    protected boolean importLine(String[] line, final long lineNumber,
            int nameIndex, int typeIndex, String[] headerValues)
            throws ClientException {
        final String name = line[nameIndex];
        final String type = line[typeIndex];
        if (StringUtils.isBlank(name)) {
            logError(lineNumber, "Missing 'name' value",
                    "label.csv.importer.missingNameValue");
            return false;
        }
        if (StringUtils.isBlank(type)) {
            logError(lineNumber, "Missing 'type' value",
                    "label.csv.importer.missingTypeValue");
            return false;
        }

        DocumentType docType = Framework.getLocalService(SchemaManager.class).getDocumentType(
                type);
        if (docType == null) {
            logError(lineNumber, "The type '%s' does not exist",
                    "label.csv.importer.notExistingType", type);
            return false;
        }

        Map<String, Serializable> values = computePropertiesMap(lineNumber,
                docType, headerValues, line);
        if (values == null) {
            // skip this line
            return false;
        }
        List<String> tags = extractTags(line, headerValues);
        return createOrUpdateDocument(lineNumber, parentPath, name, type, values, tags);
    }

    private List<String> extractTags(String[] line, String[] headerValues) {
        List<String> tags = new ArrayList<String>();
        for (int col = 0; col < headerValues.length; col++) {
            if(headerValues[col].equals(TAGS_COL) && !line[col].equals("")){
                tags.addAll(Arrays.asList(line[col].split(",")));
                break;
            }
        }
        return tags;
    }

    protected Map<String, Serializable> computePropertiesMap(long lineNumber,
            DocumentType docType, String[] headerValues, String[] line) {
        Map<String, Serializable> values = new HashMap<String, Serializable>();
        Set<String> specialHeaderEntries = new HashSet<String>(Arrays.asList(CSV_NAME_COL, CSV_TYPE_COL, TAGS_COL));
        for (int col = 0; col < headerValues.length; col++) {
            String headerValue = headerValues[col];
            String lineValue = line[col];
            lineValue = lineValue.trim();

            String fieldName = headerValue;
            if (!specialHeaderEntries.contains(headerValue)) {
                if (!docType.hasField(fieldName)) {
                    fieldName = fieldName.split(":")[1];
                }
                if (docType.hasField(fieldName)
                        && !StringUtils.isBlank(lineValue)) {
                    Serializable convertedValue = convertValue(docType,
                            fieldName, headerValue, lineValue, lineNumber);
                    if (convertedValue == null) {
                        return null;
                    }
                    values.put(headerValue, convertedValue);
                }
            }
        }
        return values;
    }

    protected Serializable convertValue(DocumentType docType, String fieldName,
            String headerValue, String stringValue, long lineNumber) {
        if (docType.hasField(fieldName)) {
            Field field = docType.getField(fieldName);
            if (field != null) {
                try {
                    Serializable fieldValue = null;
                    Type fieldType = field.getType();
                    if (fieldType.isComplexType()) {
                        if (fieldType.getName().equals(CONTENT_FILED_TYPE_NAME)) {
                            String blobsFolderPath = Framework.getProperty("nuxeo.csv.blobs.folder");
                            String path = FilenameUtils.normalize(blobsFolderPath
                                    + "/" + stringValue);
                            File file = new File(path);
                            if (file.exists()) {
                                FileBlob blob = new FileBlob(file);
                                blob.setFilename(file.getName());
                                fieldValue = blob;
                            } else {
                                logError(lineNumber,
                                        "The file '%s' does not exist",
                                        "label.csv.importer.notExistingFile",
                                        stringValue);
                                return null;
                            }
                        }
                        // other types not supported
                    } else {
                        if (fieldType.isListType()) {
                            Type listFieldType = ((ListType) fieldType).getFieldType();
                            if (listFieldType.isSimpleType()) {
                                /*
                                 * Array.
                                 */
                                fieldValue = stringValue.split(options.getListSeparatorRegex());
                            } else {
                                /*
                                 * Complex list.
                                 */
                                fieldValue = (Serializable) Arrays.asList(stringValue.split(options.getListSeparatorRegex()));
                            }
                        } else {
                            /*
                             * Primitive type.
                             */
                            Type type = field.getType();
                            if (type instanceof SimpleTypeImpl) {
                                type = type.getSuperType();
                            }
                            if (type.isSimpleType()) {
                                if (type instanceof StringType) {
                                    fieldValue = stringValue;
                                } else if (type instanceof IntegerType) {
                                    fieldValue = Integer.valueOf(stringValue);
                                } else if (type instanceof LongType) {
                                    fieldValue = Long.valueOf(stringValue);
                                } else if (type instanceof DoubleType) {
                                    fieldValue = Double.valueOf(stringValue);
                                } else if (type instanceof BooleanType) {
                                    fieldValue = Boolean.valueOf(stringValue);
                                } else if (type instanceof DateType) {
                                    fieldValue = getDateFormat().parse(
                                            stringValue);
                                }
                            }
                        }
                    }
                    return fieldValue;
                } catch (ParseException | NumberFormatException e) {
                    logError(lineNumber,
                            "Unable to convert field '%s' with value '%s'",
                            "label.csv.importer.cannotConvertFieldValue",
                            headerValue, stringValue);
                    log.debug(e, e);
                }
            }
        } else {
            logError(lineNumber, "Field '%s' does not exist on type '%s'",
                    "label.csv.importer.notExistingField", headerValue,
                    docType.getName());
        }
        return null;
    }

    protected DateFormat getDateFormat() {
        // transient field so may become null
        if (dateformat == null) {
            dateformat = new SimpleDateFormat(options.getDateFormat());
        }
        return dateformat;
    }

    protected boolean createOrUpdateDocument(long lineNumber,
                                             String parentPath, String name, String type,
                                             Map<String, Serializable> properties, List<String> tags) throws ClientException {
        Path targetPath = new Path(parentPath).append(name);
        name = targetPath.lastSegment();
        parentPath = targetPath.removeLastSegments(1).toString();
        DocumentRef docRef = new PathRef(targetPath.toString());
        boolean result;
        if (options.getCSVImporterDocumentFactory().exists(session, parentPath,
                name, type, properties)) {
            result = updateDocument(lineNumber, docRef, properties);
        } else {
            result = createDocument(lineNumber, parentPath, name, type,
                    properties);
        }
        for (String tag : tags) {
            DocumentModel document = session.getDocument(docRef);
            tagService.tag(session, document.getId(), tag, username);
        }
        return result;
    }

    protected boolean createDocument(long lineNumber, String parentPath,
            String name, String type, Map<String, Serializable> properties) {
        try {
            DocumentRef parentRef = new PathRef(parentPath);
            if (session.exists(parentRef)) {
                DocumentModel parent = session.getDocument(parentRef);

                TypeManager typeManager = Framework.getLocalService(TypeManager.class);
                if (options.checkAllowedSubTypes()
                        && !typeManager.isAllowedSubType(type, parent.getType())) {
                    logError(lineNumber, "'%s' type is not allowed in '%s'",
                            "label.csv.importer.notAllowedSubType", type,
                            parent.getType());
                } else {
                    options.getCSVImporterDocumentFactory().createDocument(
                            session, parentPath, name, type, properties);
                    importLogs.add(new CSVImportLog(lineNumber, Status.SUCCESS,
                            "Document created",
                            "label.csv.importer.documentCreated"));
                    return true;
                }
            } else {
                logError(lineNumber, "Parent document '%s' does not exist",
                        "label.csv.importer.parentDoesNotExist", parentPath);
            }
        } catch (RuntimeException e) {
            Throwable unwrappedException = unwrapException(e);
            logError(lineNumber, "Unable to create document: %s",
                    "label.csv.importer.unableToCreate",
                    unwrappedException.getMessage());
            log.debug(unwrappedException, unwrappedException);
        }
        return false;
    }

    protected boolean updateDocument(long lineNumber, DocumentRef docRef,
            Map<String, Serializable> properties) {
        if (options.updateExisting()) {
            try {
                options.getCSVImporterDocumentFactory().updateDocument(session,
                        docRef, properties);
                importLogs.add(new CSVImportLog(lineNumber, Status.SUCCESS,
                        "Document updated",
                        "label.csv.importer.documentUpdated"));
                return true;
            } catch (RuntimeException e) {
                Throwable unwrappedException = unwrapException(e);
                logError(lineNumber, "Unable to update document: %s",
                        "label.csv.importer.unableToUpdate",
                        unwrappedException.getMessage());
                log.debug(unwrappedException, unwrappedException);
            }
        } else {
            importLogs.add(new CSVImportLog(lineNumber, Status.SKIPPED,
                    "Document already exists",
                    "label.csv.importer.documentAlreadyExists"));
        }
        return false;
    }

    protected void logError(long lineNumber, String message,
            String localizedMessage, String... params) {
        importLogs.add(new CSVImportLog(lineNumber, ERROR, String.format(
                message, (Object[]) params), localizedMessage, params));
        String lineMessage = String.format("Line %d", lineNumber);
        String errorMessage = String.format(message, (Object[]) params);
        log.error(String.format("%s: %s", lineMessage, errorMessage));
    }

    protected void sendMail() throws Exception {
        UserManager userManager = Framework.getLocalService(UserManager.class);
        NuxeoPrincipal principal = userManager.getPrincipal(username);
        String email = principal.getEmail();
        if (email == null) {
            log.info(String.format(
                    "Not sending import result email to '%s', no email configured",
                    username));
            return;
        }

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(session.getRootDocument());

        CSVImporter csvImporter = Framework.getLocalService(CSVImporter.class);
        List<CSVImportLog> importLogs = csvImporter.getImportLogs(getId());
        CSVImportResult importResult = CSVImportResult.fromImportLogs(importLogs);
        List<CSVImportLog> skippedAndErrorImportLogs = csvImporter.getImportLogs(
                getId(), Status.SKIPPED, Status.ERROR);
        ctx.put("importResult", importResult);
        ctx.put("skippedAndErrorImportLogs", skippedAndErrorImportLogs);
        ctx.put("csvFilename", csvFileName);
        ctx.put("startDate", DateFormat.getInstance().format(startDate));
        ctx.put("username", username);

        DocumentModel importFolder = session.getDocument(new PathRef(parentPath));
        String importFolderUrl = getDocumentUrl(importFolder);
        ctx.put("importFolderTitle", importFolder.getTitle());
        ctx.put("importFolderUrl", importFolderUrl);
        ctx.put("userUrl", getUserUrl(principal.getName()));

        StringList to = buildRecipientsList(email);
        Expression from = Scripting.newExpression("Env[\"mail.from\"]");
        String subject = "CSV Import result of " + csvFileName;
        String message = loadTemplate(TEMPLATE_IMPORT_RESULT);

        try {
            OperationChain chain = new OperationChain("SendMail");
            chain.add(SendMail.ID).set("from", from).set("to", to).set("HTML",
                    true).set("subject", subject).set("message", message);
            Framework.getLocalService(AutomationService.class).run(ctx, chain);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        } catch (Exception e) {
            log.error(String.format(
                    "Unable to notify user '%s' for import result of '%s': %s",
                    username, csvFileName, e.getMessage()));
            log.debug(e, e);
            throw e;
        }
    }

    protected String getDocumentUrl(DocumentModel doc) throws Exception {
        return MailTemplateHelper.getDocumentUrl(doc, null);
    }

    protected String getUserUrl(String username) {
        NotificationService notificationService = NotificationServiceHelper.getNotificationService();
        Map<String, String> params = new HashMap<String, String>();
        params.put("username", username);
        DocumentView docView = new DocumentViewImpl(null, null, params);
        URLPolicyService urlPolicyService = Framework.getLocalService(URLPolicyService.class);
        return urlPolicyService.getUrlFromDocumentView("user", docView,
                notificationService.getServerUrlPrefix());
    }

    protected StringList buildRecipientsList(String userEmail) {
        String csvMailTo = Framework.getProperty("nuxeo.csv.mail.to");
        if (StringUtils.isBlank(csvMailTo)) {
            return new StringList(new String[] { userEmail });
        } else {
            return new StringList(new String[] { userEmail, csvMailTo });
        }
    }

    private static String loadTemplate(String key) {
        InputStream io = CSVImporterWork.class.getClassLoader().getResourceAsStream(
                key);
        if (io != null) {
            try {
                return IOUtils.toString(io, "UTF-8");
            } catch (IOException e) {
                throw new ClientRuntimeException(e);
            } finally {
                try {
                    io.close();
                } catch (IOException e) {
                    // nothing to do
                }
            }
        }
        return null;
    }

    public static Throwable unwrapException(Throwable t) {
        Throwable cause = null;

        if (t instanceof ClientException || t instanceof Exception) {
            cause = t.getCause();
        }

        if (cause == null) {
            return t;
        } else {
            return unwrapException(cause);
        }
    }

}
