/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thomas Roger
 */

package org.nuxeo.ecm.csv;

import java.io.Serializable;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;

/**
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.7
 */
public interface CSVImporterDocumentFactory extends Serializable {

    public void createDocument(CoreSession session, String parentPath, String name, String type,
            Map<String, Serializable> values);

    public void updateDocument(CoreSession session, DocumentRef docRef, Map<String, Serializable> values);

    /**
     * @return {@code true} if a document with the specified parentPath and name exists. {@code false} otherwise.
     * @since 8.1
     */
    public boolean exists(CoreSession session, String parentPath, String name);

    /**
     * @deprecated since 8.1
     */
    @Deprecated
    public boolean exists(CoreSession session, String parentPath, String name, String type,
            Map<String, Serializable> values);

}
