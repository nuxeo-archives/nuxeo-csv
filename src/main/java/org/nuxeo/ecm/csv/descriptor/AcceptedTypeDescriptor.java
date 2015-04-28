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

/**
 * 
 * 
 * @since 7.3
 */
@XObject("acceptedType")
public class AcceptedTypeDescriptor {
    @XNode("@type")
    private String type;

    @XNode("@enabled")
    private Boolean enabled = Boolean.TRUE;

    public AcceptedTypeDescriptor() {
        super();
    }

    public String getType() {
        return type;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    @Override
    public AcceptedTypeDescriptor clone() {
        AcceptedTypeDescriptor clone = new AcceptedTypeDescriptor();

        clone.type = type;
        clone.enabled = enabled;

        return clone;
    }

    public void merge(AcceptedTypeDescriptor other) {
        if (other.type != null) {
            type = other.type;
        }
        if (other.enabled != null) {
            enabled = other.enabled;
        }
    }
}
