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

import java.util.Comparator;

import org.apache.commons.lang.ObjectUtils;

/**
 * Compares two CSVFileHandlerDescriptor using their "order" property.
 * 
 * @since 7.3
 */
public class CSVFileHandlerDescriptorComparator implements Comparator<CSVFileHandlerDescriptor> {
    public static final CSVFileHandlerDescriptorComparator INSTANCE = new CSVFileHandlerDescriptorComparator();

    public CSVFileHandlerDescriptorComparator() {
        super();
    }

    @Override
    public int compare(CSVFileHandlerDescriptor fileHandler1, CSVFileHandlerDescriptor fileHandler2) {
        if (fileHandler1 == null && fileHandler2 == null) {
            return 0;
        }
        if (fileHandler1 == null || fileHandler2 == null) {
            return fileHandler1 == null ? -1 : 1;
        }

        int compare = ObjectUtils.compare(fileHandler1.getOrder(), fileHandler2.getOrder());

        if (compare == 0) {
            // deterministic sort, if two descriptors have the same order
            return fileHandler1.hashCode() - fileHandler2.hashCode();
        }

        return compare;
    }
}
