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

import org.nuxeo.ecm.csv.descriptor.AcceptedTypeDescriptor;
import org.nuxeo.runtime.model.SimpleContributionRegistry;

/**
 * AcceptedTypeDescriptor registry handling merge.
 * 
 * @since 7.3
 */
public class AcceptedTypeRegistry extends SimpleContributionRegistry<AcceptedTypeDescriptor> {
    public AcceptedTypeRegistry() {
        super();
    }

    @Override
    public String getContributionId(AcceptedTypeDescriptor descriptor) {
        return descriptor.getType();
    }

    @Override
    public boolean isSupportingMerge() {
        return true;
    }

    @Override
    public AcceptedTypeDescriptor clone(AcceptedTypeDescriptor orig) {
        return orig.clone();
    }

    @Override
    public void merge(AcceptedTypeDescriptor src, AcceptedTypeDescriptor dst) {
        dst.merge(src);
    }

    public Collection<AcceptedTypeDescriptor> getAcceptedTypes() {
        Collection<AcceptedTypeDescriptor> acceptedTypes = new ArrayList<>();

        for (AcceptedTypeDescriptor contrib : currentContribs.values()) {
            if (Boolean.TRUE.equals(contrib.getEnabled())) {
                acceptedTypes.add(contrib);
            }
        }

        return acceptedTypes;
    }
}
