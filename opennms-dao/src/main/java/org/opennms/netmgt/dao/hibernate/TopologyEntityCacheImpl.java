/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.dao.hibernate;

import java.util.Collections;
import java.util.List;

import org.opennms.netmgt.dao.api.TopologyEntityDao;
import org.opennms.netmgt.dao.api.TopologyEntityCache;
import org.opennms.netmgt.dao.api.TopologyEntityCache;
import org.opennms.netmgt.dao.api.TopologyEntityDao;
import org.opennms.netmgt.model.CdpLinkInfo;
import org.opennms.netmgt.model.VertexInfo;

public class TopologyEntityCacheImpl implements TopologyEntityCache {

    private List<VertexInfo> vertices;
    private List<CdpLinkInfo> cdpLinks;

    private TopologyEntityDao topologyEntityDao;

    public void refresh(){
        this.vertices = Collections.unmodifiableList(topologyEntityDao.getVertexInfos());
        this.cdpLinks = Collections.unmodifiableList(topologyEntityDao.getCdpLinkInfo());
    }

    public List<VertexInfo> getVertices(){
        return vertices;
    }

    public List<CdpLinkInfo> getCdpLinkInfos(){
        return cdpLinks;
    }

    public void setTopologyEntityDao(TopologyEntityDao topologyEntityDao){
        this.topologyEntityDao = topologyEntityDao;
    }
}
