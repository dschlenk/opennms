/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
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

package org.opennms.features.topology.plugins.topo.linkd.internal;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.LldpUtils.LldpPortIdSubType;
import org.opennms.features.topology.api.browsers.ContentType;
import org.opennms.features.topology.api.browsers.SelectionAware;
import org.opennms.features.topology.api.browsers.SelectionChangedListener;
import org.opennms.features.topology.api.topo.AbstractTopologyProvider;
import org.opennms.features.topology.api.topo.AbstractVertex;
import org.opennms.features.topology.api.topo.Defaults;
import org.opennms.features.topology.api.topo.GraphProvider;
import org.opennms.features.topology.api.topo.SimpleConnector;
import org.opennms.features.topology.api.topo.Vertex;
import org.opennms.features.topology.api.topo.VertexRef;
import org.opennms.netmgt.dao.api.BridgeTopologyDao;
import org.opennms.netmgt.dao.api.CdpElementDao;
import org.opennms.netmgt.dao.api.CdpLinkDao;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.IpNetToMediaDao;
import org.opennms.netmgt.dao.api.IsIsElementDao;
import org.opennms.netmgt.dao.api.IsIsLinkDao;
import org.opennms.netmgt.dao.api.LldpElementDao;
import org.opennms.netmgt.dao.api.LldpLinkDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.OspfLinkDao;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.opennms.netmgt.dao.api.TopologyDao;
import org.opennms.netmgt.model.CdpElement;
import org.opennms.netmgt.model.CdpLink;
import org.opennms.netmgt.model.CdpLink.CiscoNetworkProtocolType;
import org.opennms.netmgt.model.BridgeMacLink;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.IpNetToMedia;
import org.opennms.netmgt.model.IsIsElement;
import org.opennms.netmgt.model.IsIsLink;
import org.opennms.netmgt.model.LldpElement;
import org.opennms.netmgt.model.LldpLink;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.OspfLink;
import org.opennms.netmgt.model.PrimaryType;
import org.opennms.netmgt.model.topology.BridgePort;
import org.opennms.netmgt.model.topology.BridgeTopologyException;
import org.opennms.netmgt.model.topology.BroadcastDomain;
import org.opennms.netmgt.model.topology.SharedSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;

public class LinkdTopologyProvider extends AbstractTopologyProvider implements GraphProvider {

    private static Logger LOG = LoggerFactory.getLogger(LinkdTopologyProvider.class);

    private TransactionOperations m_transactionOperations;
    private NodeDao m_nodeDao;
    private SnmpInterfaceDao m_snmpInterfaceDao;
    private IpInterfaceDao m_ipInterfaceDao;
    private TopologyDao m_topologyDao;
    private FilterManager m_filterManager;

    private LldpLinkDao m_lldpLinkDao;
    private LldpElementDao m_lldpElementDao;
    private CdpLinkDao m_cdpLinkDao;
    private CdpElementDao m_cdpElementDao;
    private OspfLinkDao m_ospfLinkDao;
    private IsIsLinkDao m_isisLinkDao;
    private IsIsElementDao m_isisElementDao;
    private BridgeTopologyDao m_bridgeTopologyDao;
    private IpNetToMediaDao m_ipNetToMediaDao;

    private Map<Integer, OnmsNode> m_nodeMap;
    private Map<Integer, List<OnmsSnmpInterface>> m_nodeToOnmsSnmpMap;
    private Map<String, List<OnmsIpInterface>> m_macToOnmsIpMap;
    private Map<Integer, List<OnmsIpInterface>> m_nodeToOnmsIpMap;
    private Map<Integer, OnmsIpInterface> m_nodeToOnmsIpPrimaryMap;
    private Map<InetAddress, OnmsIpInterface>  m_ipToOnmsIpMap;

    private final Timer m_loadFullTimer;
    private final Timer m_loadNodesTimer;
    private final Timer m_loadIpInterfacesTimer;
    private final Timer m_loadSnmpInterfacesTimer;
    private final Timer m_loadIpNetToMediaTimer;
    private final Timer m_loadLldpLinksTimer;
    private final Timer m_loadOspfLinksTimer;
    private final Timer m_loadCdpLinksTimer;
    private final Timer m_loadIsisLinksTimer;
    private final Timer m_loadBridgeLinksTimer;
    private final Timer m_loadVerticesTimer;
    private final Timer m_loadEdgesTimer;

    public static final String TOPOLOGY_NAMESPACE_LINKD = "nodes";
    
    public enum ProtocolSupported {
        LLDP,
        OSPF,
        ISIS,
        BRIDGE,
        CDP
    }

    private SelectionAware selectionAwareDelegate = new LinkdSelectionAware();

    public LinkdTopologyProvider(MetricRegistry registry) {
        super(TOPOLOGY_NAMESPACE_LINKD);
        Objects.requireNonNull(registry);
        m_loadFullTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "full"));
        m_loadNodesTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "nodes"));
        m_loadIpInterfacesTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "ipinterfaces"));
        m_loadSnmpInterfacesTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "snmpinterfaces"));
        m_loadIpNetToMediaTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "ipnettomedia"));
        m_loadLldpLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "lldp"));
        m_loadOspfLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "ospf"));
        m_loadCdpLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "cdp"));
        m_loadIsisLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "isis"));
        m_loadBridgeLinksTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "links", "bridge"));
        m_loadVerticesTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "vertices", "none"));
        m_loadEdgesTimer = registry.timer(MetricRegistry.name("enlinkd", "load", "edges", "none"));
    }


    @Override
    public SelectionChangedListener.Selection getSelection(List<VertexRef> selectedVertices, ContentType type) {
       return selectionAwareDelegate.getSelection(selectedVertices, type);
    }

    @Override
    public boolean contributesTo(ContentType type) {
        return selectionAwareDelegate.contributesTo(type);
    }
    
    protected final LinkdEdge connectCloudMacVertices(String targetmac, VertexRef sourceRef, VertexRef targetRef) {
        SimpleConnector source = new SimpleConnector(sourceRef.getNamespace(), sourceRef.getId()+"-"+targetRef.getId()+"-connector", sourceRef);
        SimpleConnector target = new SimpleConnector(targetRef.getNamespace(), targetRef.getId()+"-"+sourceRef.getId()+"-connector", targetRef);

        LinkdEdge edge = new LinkdEdge(targetRef.getId()+":"+targetmac, source, target, ProtocolSupported.BRIDGE);
        edge.setTargetEndPoint(targetmac);
        addEdges(edge);
        
        return edge;
    }

    protected final LinkdEdge connectVertices(BridgePort targetport, VertexRef sourceRef, VertexRef targetRef) {
        SimpleConnector source = new SimpleConnector(sourceRef.getNamespace(), sourceRef.getId()+"-"+targetRef.getId()+"-connector", sourceRef);
        SimpleConnector target = new SimpleConnector(targetRef.getNamespace(), targetRef.getId()+"-"+sourceRef.getId()+"-connector", targetRef);

        LinkdEdge edge = new LinkdEdge(targetRef.getId()+":"+targetport.getBridgePort(), source, target, ProtocolSupported.BRIDGE);
        edge.setTargetNodeid(targetport.getNodeId());
        if (targetport.getBridgePortIfIndex() != null)
            edge.setTargetEndPoint(String.valueOf(targetport.getBridgePortIfIndex()));
        addEdges(edge);
        
        return edge;
    }

    protected final LinkdEdge connectVertices(BridgeMacLink link, VertexRef sourceRef, VertexRef targetRef) {
        SimpleConnector source = new SimpleConnector(sourceRef.getNamespace(), sourceRef.getId()+"-"+link.getId()+"-connector", sourceRef);
        SimpleConnector target = new SimpleConnector(targetRef.getNamespace(), targetRef.getId()+"-"+link.getId()+"-connector", targetRef);

        LinkdEdge edge = new LinkdEdge(String.valueOf(link.getId()), source, target,ProtocolSupported.BRIDGE);
        edge.setSourceNodeid(link.getNode().getId());
        if (link.getBridgePortIfIndex() != null)
            edge.setSourceEndPoint(String.valueOf(link.getBridgePortIfIndex()));
        edge.setTargetEndPoint(String.valueOf(link.getMacAddress()));
        addEdges(edge);
        
        return edge;
    }
    
    protected final LinkdEdge connectVertices(LinkdEdgeDetail<?,?> linkdetail, ProtocolSupported discoveredBy) {
        SimpleConnector source = new SimpleConnector(linkdetail.getSource().getNamespace(), linkdetail.getSource().getId()+"-"+linkdetail.getId()+"-connector", linkdetail.getSource());
        SimpleConnector target = new SimpleConnector(linkdetail.getTarget().getNamespace(), linkdetail.getTarget().getId()+"-"+linkdetail.getId()+"-connector", linkdetail.getTarget());

        LinkdEdge edge = new LinkdEdge(linkdetail.getId(), source, target,discoveredBy);
        try {
            edge.setSourceNodeid(Integer.parseInt(linkdetail.getSource().getId()));
        } catch (NumberFormatException e) {
            
        }
        try {
            edge.setTargetNodeid(Integer.parseInt(linkdetail.getTarget().getId()));
        } catch (NumberFormatException e) {
            
        }
        if (linkdetail.getSourceIfIndex() != null) {
            edge.setSourceEndPoint(String.valueOf(linkdetail.getSourceIfIndex()));
        }
        if (linkdetail.getTargetIfIndex() != null) {
            edge.setTargetEndPoint(String.valueOf(linkdetail.getTargetIfIndex()));
        }

        addEdges(edge);
        
        return edge;
    }
    
    private void loadVertices() {
        for (Entry<Integer, OnmsNode> entry: m_nodeMap.entrySet()) {
            Integer nodeId = entry.getKey();
            OnmsNode node = entry.getValue();
            OnmsIpInterface primary = m_nodeToOnmsIpPrimaryMap.get(nodeId);
            addVertices(LinkdVertex.createLinkdVertex(node,primary));
        }
    }
    
    private void loadEdges() {

        Timer.Context context = m_loadLldpLinksTimer.time();
        try{
            getLldpLinks();
            LOG.info("loadEdges: LldpLink loaded");
        } catch (Exception e){
            LOG.error("Loading LldpLink failed: {}",e.getMessage(),e);
        } finally {
            context.stop();
        }

        context = m_loadOspfLinksTimer.time();
        try{
            getOspfLinks();
            LOG.info("loadEdges: OspfLink loaded");
        } catch (Exception e){
            LOG.error("Loading OspfLink failed: {}",e.getMessage(),e);
        } finally {
            context.stop();
        }

        context = m_loadCdpLinksTimer.time();
        try{
            getCdpLinks();
            LOG.info("loadEdges: CdpLink loaded");
        } catch (Exception e){
            LOG.error("Loading CdpLink failed: {}",e.getMessage(),e);
        } finally {
            context.stop();
        }

        context = m_loadIsisLinksTimer.time();
        try{
            getIsIsLinks();
            LOG.info("loadEdges: IsIsLink loaded");
        } catch (Exception e){
            LOG.error("Exception getting IsIs link: "+e.getMessage(),e);
        } finally {
            context.stop();
        }

        context = m_loadBridgeLinksTimer.time();
        try{
            getBridgeLinks();
            LOG.info("loadEdges: BridgeLink loaded");
        } catch (Exception e){
            LOG.error("Loading BridgeLink failed: {}",e.getMessage(),e);
        } finally {
            context.stop();
        }
    }


    private void getLldpLinks() {
        // Index the nodes by sysName
        final Map<String, OnmsNode> nodesbysysname = new HashMap<>();
        for (OnmsNode node: m_nodeMap.values()) {
            if (node.getSysName() != null) {
                nodesbysysname.putIfAbsent(node.getSysName(), node);
            }
        }

        // Index the LLDP elements by node id
        Map<Integer, LldpElement> lldpelementmap = new HashMap<Integer, LldpElement>();
        for (LldpElement lldpelement: m_lldpElementDao.findAll()) {
            lldpelementmap.put(lldpelement.getNode().getId(), lldpelement);
        }

        // Pull all of the LLDP links and index them by remote chassis id
        List<LldpLink> allLinks = m_lldpLinkDao.findAll();
        Map<String, List<LldpLink>> linksByRemoteChassisId = new HashMap<>();
        for (LldpLink link : allLinks) {
            final String remoteChassisId = link.getLldpRemChassisId();
            List<LldpLink> linksWithRemoteChassisId = linksByRemoteChassisId.get(remoteChassisId);
            if (linksWithRemoteChassisId == null) {
                linksWithRemoteChassisId = new ArrayList<>();
                linksByRemoteChassisId.put(remoteChassisId, linksWithRemoteChassisId);
            }
            linksWithRemoteChassisId.add(link);
        }

        Set<LinkdLldpDetail> combinedLinkDetails = new HashSet<LinkdLldpDetail>();
        Set<Integer> parsed = new HashSet<Integer>();
        for (LldpLink sourceLink : allLinks) {
            if (parsed.contains(sourceLink.getId())) {
                continue;
            }
            LOG.debug("loadtopology: lldp link with id '{}' link '{}' ", sourceLink.getId(), sourceLink);
            LldpElement sourceLldpElement = lldpelementmap.get(sourceLink.getNode().getId());
            LldpLink targetLink = null;

            // Limit the candidate links by only choosing those have a remote chassis id matching the chassis id of the source link
            for (LldpLink link : linksByRemoteChassisId.getOrDefault(sourceLldpElement.getLldpChassisId(), Collections.emptyList())) {
                if (parsed.contains(link.getId())) {
                    continue;
                }

                if (sourceLink.getId().intValue() == link.getId().intValue()) {
                    continue;
                }
                LOG.debug("loadtopology: checking lldp link with id '{}' link '{}' ", link.getId(), link);
                LldpElement element = lldpelementmap.get(link.getNode().getId());
                // Compare the chassis id on the other end of the link
                if (!sourceLink.getLldpRemChassisId().equals(element.getLldpChassisId())) {
                    continue;
                }
                boolean bool1 = sourceLink.getLldpRemPortId().equals(link.getLldpPortId()) && link.getLldpRemPortId().equals(sourceLink.getLldpPortId());
                boolean bool3 = sourceLink.getLldpRemPortIdSubType() == link.getLldpPortIdSubType() && link.getLldpRemPortIdSubType() == sourceLink.getLldpPortIdSubType();

                if (bool1 && bool3) {
                    targetLink=link;
                    LOG.info("loadtopology: found lldp mutual link: '{}' and '{}' ", sourceLink,targetLink);
                    break;
                }
            }

            if (targetLink == null && sourceLink.getLldpRemSysname() != null) {
                final OnmsNode node = nodesbysysname.get(sourceLink.getLldpRemSysname());
                if (node != null) {
                    targetLink = reverseLldpLink(node, sourceLldpElement, sourceLink);
                    LOG.info("loadtopology: found lldp link using lldp rem sysname: '{}' and '{}'", sourceLink, targetLink);
                }
            }

            if (targetLink == null) {
                LOG.info("loadtopology: cannot found target node for link: '{}'", sourceLink);
                continue;
            }
                
            parsed.add(sourceLink.getId());
            parsed.add(targetLink.getId());
            LinkdVertex source = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, sourceLink.getNode().getNodeId());
            source.getProtocolSupported().add(ProtocolSupported.LLDP);
            LinkdVertex target = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, targetLink.getNode().getNodeId());
            target.getProtocolSupported().add(ProtocolSupported.LLDP);
            combinedLinkDetails.add(new LinkdLldpDetail(this, Math.min(sourceLink.getId(), targetLink.getId()) + "|" + Math.max(sourceLink.getId(), targetLink.getId()),
                                                       source, sourceLink, target, targetLink));

        }

        for (LinkdLldpDetail linkDetail : combinedLinkDetails) {
            LinkdEdge edge = connectVertices(linkDetail,ProtocolSupported.LLDP);
            edge.setTooltipText(LinkdEdge.getEdgeTooltipText(linkDetail,m_nodeToOnmsSnmpMap));
        }
    }

    private void getOspfLinks() {
        List<OspfLink> allLinks =  getOspfLinkDao().findAll();
        Set<LinkdOspfDetail> combinedLinkDetails = new HashSet<LinkdOspfDetail>();
        Set<Integer> parsed = new HashSet<Integer>();
        for(OspfLink sourceLink : allLinks) {
            if (parsed.contains(sourceLink.getId())) 
                continue;
            LOG.debug("loadtopology: ospf link with id '{}'", sourceLink.getId());
            for (OspfLink targetLink : allLinks) {
                if (sourceLink.getId().intValue() == targetLink.getId().intValue() || parsed.contains(targetLink.getId())) 
                    continue;
                LOG.debug("loadtopology: checking ospf link with id '{}'", targetLink.getId());
                if(sourceLink.getOspfRemIpAddr().equals(targetLink.getOspfIpAddr()) && targetLink.getOspfRemIpAddr().equals(sourceLink.getOspfIpAddr())) {
                    LOG.info("loadtopology: found ospf mutual link: '{}' and '{}' ", sourceLink,targetLink);
                    parsed.add(sourceLink.getId());
                    parsed.add(targetLink.getId());
                    LinkdVertex source = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, sourceLink.getNode().getNodeId());
                    source.getProtocolSupported().add(ProtocolSupported.OSPF);
                    LinkdVertex target = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, targetLink.getNode().getNodeId());
                    target.getProtocolSupported().add(ProtocolSupported.OSPF);
                    LinkdOspfDetail linkDetail = new LinkdOspfDetail(
                            Math.min(sourceLink.getId(), targetLink.getId()) + "|" + Math.max(sourceLink.getId(), targetLink.getId()),
                            source, sourceLink, target, targetLink);
                    combinedLinkDetails.add(linkDetail);
                    break;
                }
            }
        }

        for (LinkdOspfDetail linkDetail : combinedLinkDetails) {
            LinkdEdge edge = connectVertices(linkDetail,ProtocolSupported.OSPF);
            edge.setTooltipText(LinkdEdge.getEdgeTooltipText(linkDetail,m_nodeToOnmsSnmpMap));
        }
    }

    private void getCdpLinks() {
        Map<Integer, CdpElement> cdpelementmap = new HashMap<Integer, CdpElement>();
        for (CdpElement cdpelement: m_cdpElementDao.findAll()) {
            cdpelementmap.put(cdpelement.getNode().getId(), cdpelement);
        }

        List<CdpLink> allLinks = m_cdpLinkDao.findAll();
        Set<LinkdCdpDetail> combinedLinkDetails = new HashSet<LinkdCdpDetail>();
        Set<Integer> parsed = new HashSet<Integer>();

        for (CdpLink sourceLink : allLinks) {
            if (parsed.contains(sourceLink.getId())) { 
                continue;
            }
            LOG.debug("loadtopology: cdp link with id '{}' link '{}' ", sourceLink.getId(), sourceLink);
            CdpElement sourceCdpElement = cdpelementmap.get(sourceLink.getNode().getId());
            CdpLink targetLink = null;
            for (CdpLink link : allLinks) {
                if (sourceLink.getId().intValue() == link.getId().intValue()|| parsed.contains(link.getId()))
                    continue;
                LOG.debug("loadtopology: checking cdp link with id '{}' link '{}' ", link.getId(), link);
                CdpElement element = cdpelementmap.get(link.getNode().getId());
                //Compare the remote data to the targetNode element data
                if (!sourceLink.getCdpCacheDeviceId().equals(element.getCdpGlobalDeviceId()) || !link.getCdpCacheDeviceId().equals(sourceCdpElement.getCdpGlobalDeviceId())) 
                    continue;

                if (sourceLink.getCdpInterfaceName().equals(link.getCdpCacheDevicePort()) && link.getCdpInterfaceName().equals(sourceLink.getCdpCacheDevicePort())) {
                    targetLink=link;
                    LOG.info("loadtopology: found cdp mutual link: '{}' and '{}' ", sourceLink,targetLink);
                    break;
                }
            }
            
            if (targetLink == null) {
                if (sourceLink.getCdpCacheAddressType() == CiscoNetworkProtocolType.ip) {
                    try {
                        InetAddress targetAddress = InetAddressUtils.addr(sourceLink.getCdpCacheAddress());
                        if (m_ipToOnmsIpMap.containsKey(targetAddress)) {
                            targetLink = reverseCdpLink(m_ipToOnmsIpMap.get(targetAddress), sourceCdpElement, sourceLink ); 
                            LOG.info("loadtopology: found cdp link using cdp cache address: '{}' and '{}'", sourceLink, targetLink);
                        }
                    } catch (Exception e) {
                        LOG.warn("loadtopology: cannot convert ip address: {}", sourceLink.getCdpCacheAddress(), e);
                    }
                }
            }
            
            if (targetLink == null) {
                LOG.info("loadtopology: cannot found target node for link: '{}'", sourceLink);
                continue;
            }
                
            parsed.add(sourceLink.getId());
            parsed.add(targetLink.getId());
            LinkdVertex source = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, sourceLink.getNode().getNodeId());
            source.getProtocolSupported().add(ProtocolSupported.CDP);
            LinkdVertex target = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, targetLink.getNode().getNodeId());
            target.getProtocolSupported().add(ProtocolSupported.CDP);
            combinedLinkDetails.add(new LinkdCdpDetail(Math.min(sourceLink.getId(), targetLink.getId()) + "|" + Math.max(sourceLink.getId(), targetLink.getId()),
                                                       source, sourceLink, target, targetLink));
        }
        
        for (LinkdCdpDetail linkDetail : combinedLinkDetails) {
            LinkdEdge edge = connectVertices(linkDetail,ProtocolSupported.CDP);
            edge.setTooltipText(LinkdEdge.getEdgeTooltipText(linkDetail,m_nodeToOnmsSnmpMap));
        }
    }
    
    private void getIsIsLinks(){

        Map<Integer, IsIsElement> elementmap = new HashMap<Integer, IsIsElement>();
        for (IsIsElement element: m_isisElementDao.findAll()) {
            elementmap.put(element.getNode().getId(), element);
        }

        List<IsIsLink> isislinks = m_isisLinkDao.findAll();
        Set<LinkdIsIsDetail> combinedLinkDetails = new HashSet<LinkdIsIsDetail>();
        Set<Integer> parsed = new HashSet<Integer>();

        for (IsIsLink sourceLink : isislinks) {
            if (parsed.contains(sourceLink.getId())) { 
                continue;
            }
            LOG.debug("loadtopology: isis link with id '{}' link '{}' ", sourceLink.getId(), sourceLink);
            IsIsElement sourceElement = elementmap.get(sourceLink.getNode().getId());
            IsIsLink targetLink = null;
            for (IsIsLink link : isislinks) {
                if (sourceLink.getId().intValue() == link.getId().intValue()|| parsed.contains(link.getId()))
                    continue;
                LOG.debug("loadtopology: checking isis link with id '{}' link '{}' ", link.getId(), link);
                IsIsElement targetElement = elementmap.get(link.getNode().getId());
                //Compare the remote data to the targetNode element data
                if (!sourceLink.getIsisISAdjNeighSysID().equals(targetElement.getIsisSysID())  
                        || !link.getIsisISAdjNeighSysID().equals(sourceElement.getIsisSysID())) { 
                    continue;
                }

                if (sourceLink.getIsisISAdjIndex().intValue() == 
                        link.getIsisISAdjIndex().intValue()  ) {
                    targetLink=link;
                    LOG.info("loadtopology: found isis mutual link: '{}' and '{}' ", sourceLink,targetLink);
                    break;
                }
            }
            
            if (targetLink == null) {
                LOG.info("loadtopology: cannot found isis target node for link: '{}'", sourceLink);
                continue;
            }

            parsed.add(sourceLink.getId());
            parsed.add(targetLink.getId());
            LinkdVertex source = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, sourceLink.getNode().getNodeId());
            source.getProtocolSupported().add(ProtocolSupported.ISIS);
            LinkdVertex target = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, targetLink.getNode().getNodeId());
            target.getProtocolSupported().add(ProtocolSupported.ISIS);
            combinedLinkDetails.add(new LinkdIsIsDetail(Math.min(sourceLink.getId(), targetLink.getId()) + "|" + Math.max(sourceLink.getId(), targetLink.getId()),
                                                       source, sourceLink, target, targetLink));
        }

        for (LinkdIsIsDetail linkDetail : combinedLinkDetails) {
            LinkdEdge edge = connectVertices(linkDetail,ProtocolSupported.ISIS);
            edge.setTooltipText(LinkdEdge.getEdgeTooltipText(linkDetail,m_nodeToOnmsSnmpMap));
        }
    }

    
    private void getBridgeLinks() throws BridgeTopologyException {
        
        for (BroadcastDomain domain: m_bridgeTopologyDao.load()) {
            LOG.info("loadtopology: parsing broadcast Domain: {}", domain.getBridgeNodesOnDomain());
            parseDomain(domain);
        }
    }
    
    private void parseDomain(BroadcastDomain domain) throws BridgeTopologyException {
        for (SharedSegment segment: domain.getSharedSegments()) {
            LOG.info("loadtopology: parsing segment: {}", segment.getBridgeIdsOnSegment());
            parseSegment(segment);
        }
    }
    
    private void parseSegment(SharedSegment segment) throws BridgeTopologyException {
        Map<BridgePort,Vertex> portToVertexMap = new HashMap<BridgePort, Vertex>();
        for (BridgePort bp : segment.getBridgePortsOnSegment()) {
            LinkdVertex vertex = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, bp.getNodeId().toString());
            vertex.getProtocolSupported().add(ProtocolSupported.BRIDGE);
            portToVertexMap.put(bp,vertex);
        }
        
        Map<String,Vertex> macToVertexMap = new HashMap<String, Vertex>();
        Set<String> macswithoutip = new HashSet<String>();
        for (String mac: segment.getMacsOnSegment()) {
            if (m_macToOnmsIpMap.containsKey(mac) && m_macToOnmsIpMap.get(mac).size() > 0) {
               List<OnmsIpInterface> targetInterfaces = m_macToOnmsIpMap.get(mac);
               OnmsIpInterface targetIp = targetInterfaces.get(0);
               LinkdVertex vertex = (LinkdVertex)getVertex(TOPOLOGY_NAMESPACE_LINKD, targetIp.getNode().getNodeId());
               vertex.getProtocolSupported().add(ProtocolSupported.BRIDGE);
               macToVertexMap.put(mac,vertex);
            } else {
                macswithoutip.add(mac);
            }
        }
        if (portToVertexMap.size() == 2 && 
            segment.getMacsOnSegment().size() == 0) {
            Vertex source = null;
            Vertex target = null;
            BridgePort sourcebp = null;
            BridgePort targetbp = null;
            for (BridgePort bp: portToVertexMap.keySet()) {
                if (bp.getNodeId() == segment.getDesignatedBridge()) {
                    source = portToVertexMap.get(bp);
                    sourcebp = bp;
                    continue;
                } 
                target = portToVertexMap.get(bp);
                targetbp=bp;
            }
            LinkdBridgeDetail detail = new LinkdBridgeDetail(source, sourcebp, target, targetbp);
            LinkdEdge edge = connectVertices(detail,ProtocolSupported.BRIDGE);
            edge.setTooltipText(LinkdEdge.getEdgeTooltipText(detail,m_nodeToOnmsSnmpMap));
            return;
        }
        if (portToVertexMap.size() == 1 && 
                macToVertexMap.size() == 1 && 
                segment.getMacsOnSegment().size() == 1) {
            Vertex source = null;
            Vertex target = null;
            List<OnmsIpInterface> targetInterfaces;
            BridgePort sourcebp = null;
            for (BridgePort bp: portToVertexMap.keySet()) {
                sourcebp = bp;
                source = portToVertexMap.get(bp);      
            }
            for (String mac : macToVertexMap.keySet()) { 
                target = macToVertexMap.get(mac);
                targetInterfaces = m_macToOnmsIpMap.get(mac);
                LinkdEdge edge = connectVertices(sourcebp, source, target);
                edge.setTooltipText(LinkdEdge.getEdgeTooltipText(sourcebp,source,target, targetInterfaces,m_nodeToOnmsSnmpMap,mac));
            }
            return;
        }
        String cloudId = segment.getDesignatedBridge()+":"+segment.getDesignatedPort().getBridgePort();
        AbstractVertex cloudVertex = addVertex(cloudId, 0, 0);
        cloudVertex.setLabel("");
        cloudVertex.setIconKey("cloud");
        cloudVertex.setTooltipText("Shared Segment: designated node: " + m_nodeMap.get(segment.getDesignatedBridge()).getLabel() + " port: " + segment.getDesignatedPort().getBridgePort());
        addVertices(cloudVertex);
        LOG.info("loadtopology: adding cloud: id: '{}', {}", cloudId, cloudVertex.getTooltipText() );
        for (BridgePort targetport: portToVertexMap.keySet()) {
            Vertex target = portToVertexMap.get(targetport);
            LinkdEdge edge = connectVertices(targetport, cloudVertex, target);
            edge.setTooltipText(LinkdEdge.getEdgeTooltipText(targetport,target,m_nodeToOnmsSnmpMap));
        }
        for (String mac: macToVertexMap.keySet()) {
            Vertex target = macToVertexMap.get(mac);
            LinkdEdge edge = connectCloudMacVertices(mac, cloudVertex, target);
            edge.setTooltipText(LinkdEdge.getEdgeTooltipText(mac,target,m_macToOnmsIpMap.get(mac)));
        }
    }

    public TransactionOperations getTransactionOperations() {
        return m_transactionOperations;
    }

    public void setTransactionOperations(TransactionOperations transactionOperations) {
        m_transactionOperations = transactionOperations;
    }

    public SnmpInterfaceDao getSnmpInterfaceDao() {
        return m_snmpInterfaceDao;
    }

    public void setSnmpInterfaceDao(SnmpInterfaceDao snmpInterfaceDao) {
        m_snmpInterfaceDao = snmpInterfaceDao;
    }

    public NodeDao getNodeDao() {
        return m_nodeDao;
    }

    public void setNodeDao(NodeDao nodeDao) {
        m_nodeDao = nodeDao;
    }

    public void setIpInterfaceDao(IpInterfaceDao ipInterfaceDao) {
        m_ipInterfaceDao = ipInterfaceDao;
    }

    public void setTopologyDao(TopologyDao topologyDao) {
        m_topologyDao = topologyDao;
    }

    public void setFilterManager(FilterManager filterManager) {
        m_filterManager = filterManager;
    }

    public FilterManager getFilterManager() {
        return m_filterManager;
    }

    public IpInterfaceDao getIpInterfaceDao() {
        return m_ipInterfaceDao;
    }

    public void setLldpLinkDao(LldpLinkDao lldpLinkDao) {
        m_lldpLinkDao = lldpLinkDao;
    }

    public LldpLinkDao getLldpLinkDao() {
        return m_lldpLinkDao;
    }

    public void setLldpElementDao(LldpElementDao lldpElementDao) {
        m_lldpElementDao = lldpElementDao;
    }

    public LldpElementDao getLldpElementDao() {
        return m_lldpElementDao;
    }

    public void setOspfLinkDao(OspfLinkDao ospfLinkDao) {
        m_ospfLinkDao = ospfLinkDao;
    }

    public OspfLinkDao getOspfLinkDao(){
        return m_ospfLinkDao;
    }

    public IsIsLinkDao getIsisLinkDao() {
        return m_isisLinkDao;
    }

    public void setIsisLinkDao(IsIsLinkDao isisLinkDao) {
        m_isisLinkDao = isisLinkDao;
    }

    public IsIsElementDao getIsisElementDao() {
        return m_isisElementDao;
    }

    public void setIsisElementDao(IsIsElementDao isisElementDao) {
        m_isisElementDao = isisElementDao;
    }

    public BridgeTopologyDao getBridgeTopologyDao() {
        return m_bridgeTopologyDao;
    }

    public void setBridgeTopologyDao(BridgeTopologyDao bridgeTopologyDao) {
        m_bridgeTopologyDao = bridgeTopologyDao;
    }

    public IpNetToMediaDao getIpNetToMediaDao() {
        return m_ipNetToMediaDao;
    }
    
    public void setIpNetToMediaDao(IpNetToMediaDao ipNetToMediaDao) {
        m_ipNetToMediaDao = ipNetToMediaDao;
    }

    public CdpLinkDao getCdpLinkDao() {
        return m_cdpLinkDao;
    }

    public void setCdpLinkDao(CdpLinkDao cdpLinkDao) {
        m_cdpLinkDao = cdpLinkDao;
    }

    public CdpElementDao getCdpElementDao() {
        return m_cdpElementDao;
    }

    public void setCdpElementDao(CdpElementDao cdpElementDao) {
        m_cdpElementDao = cdpElementDao;
    }


    private CdpLink reverseCdpLink(OnmsIpInterface iface, CdpElement element, CdpLink link) {
        CdpLink reverseLink = new CdpLink();
        reverseLink.setId(-link.getId());
        reverseLink.setNode(iface.getNode());
        reverseLink.setCdpCacheIfIndex(iface.getIfIndex());
        reverseLink.setCdpInterfaceName(link.getCdpCacheDevicePort());
        reverseLink.setCdpCacheDeviceId(element.getCdpGlobalDeviceId());
        reverseLink.setCdpCacheDevicePort(link.getCdpInterfaceName());
        return reverseLink;
    }
    
    private LldpLink reverseLldpLink(OnmsNode sourcenode, LldpElement element, LldpLink link) {
        LldpLink reverseLink = new LldpLink();
        reverseLink.setId(-link.getId());
        reverseLink.setNode(sourcenode);
        
        reverseLink.setLldpLocalPortNum(0);
        reverseLink.setLldpPortId(link.getLldpRemPortId());
        reverseLink.setLldpPortIdSubType(link.getLldpRemPortIdSubType());
        reverseLink.setLldpPortDescr(link.getLldpRemPortDescr());
        if (link.getLldpRemPortIdSubType() == LldpPortIdSubType.LLDP_PORTID_SUBTYPE_LOCAL) {
            try {
                reverseLink.setLldpPortIfindex(Integer.getInteger(link.getLldpRemPortId()));
            } catch (Exception e) {
                LOG.debug("reverseLldpLink: cannot create ifindex from  LldpRemPortId '{}'", link.getLldpRemPortId());
            }
        }

        reverseLink.setLldpRemChassisId(element.getLldpChassisId());
        reverseLink.setLldpRemChassisIdSubType(element.getLldpChassisIdSubType());
        reverseLink.setLldpRemSysname(element.getLldpSysname());
        
        reverseLink.setLldpRemPortId(link.getLldpPortId());
        reverseLink.setLldpRemPortIdSubType(link.getLldpPortIdSubType());
        reverseLink.setLldpRemPortDescr(link.getLldpPortDescr());
        
        reverseLink.setLldpLinkCreateTime(link.getLldpLinkCreateTime());
        reverseLink.setLldpLinkLastPollTime(link.getLldpLinkLastPollTime());
        
        return reverseLink;
    }
    
    @Override
    public Defaults getDefaults() {
        return new Defaults()
                .withSemanticZoomLevel(Defaults.DEFAULT_SEMANTIC_ZOOM_LEVEL)
                .withPreferredLayout("D3 Layout") // D3 Layout
                .withCriteria(() -> {
                    final OnmsNode node = m_topologyDao.getDefaultFocusPoint();

                    if (node != null) {
                        final Vertex defaultVertex = getVertex(TOPOLOGY_NAMESPACE_LINKD, node.getNodeId());
                        if (defaultVertex != null) {
                            return Lists.newArrayList(LinkdHopCriteria.createCriteria(node.getNodeId(), node.getLabel()));
                        }
                    }
                    return Lists.newArrayList();
                });
    }

    @Transactional
    private void doRefresh() {        
        Timer.Context vcontext = m_loadNodesTimer.time();
        try {
            for (OnmsNode node: m_nodeDao.findAll()) {
                m_nodeMap.put(node.getId(), node);
            }
            LOG.info("refresh: Onms nodes loaded");
        } catch (Exception e){
            LOG.error("Loading Onms nodes failed: {}",e.getMessage(),e);
        } finally {
            vcontext.stop();
        }

        vcontext = m_loadIpInterfacesTimer.time();
        try {
            Set<InetAddress> duplicatedips = new HashSet<InetAddress>();
            for (OnmsIpInterface ip: m_ipInterfaceDao.findAll()) {
                if (!m_nodeToOnmsIpMap.containsKey(ip.getNode().getId())) {
                    m_nodeToOnmsIpMap.put(ip.getNode().getId(), new ArrayList<OnmsIpInterface>());
                    m_nodeToOnmsIpPrimaryMap.put(ip.getNode().getId(), ip);
                }
                m_nodeToOnmsIpMap.get(ip.getNode().getId()).add(ip);
                if (ip.getIsSnmpPrimary().equals(PrimaryType.PRIMARY)) {
                    m_nodeToOnmsIpPrimaryMap.put(ip.getNode().getId(), ip);
                }

                if (duplicatedips.contains(ip.getIpAddress())) {
                    LOG.debug("refresh: found duplicated ip {}, skipping ", InetAddressUtils.str(ip.getIpAddress()));
                    continue;
                }
                if (m_ipToOnmsIpMap.containsKey(ip.getIpAddress())) {
                    LOG.debug("refresh: found duplicated ip {}, skipping ", InetAddressUtils.str(ip.getIpAddress()));
                    duplicatedips.add(ip.getIpAddress());
                    continue;
                }
                m_ipToOnmsIpMap.put(ip.getIpAddress(), ip);
            }
            for (InetAddress duplicated: duplicatedips) {
                m_ipToOnmsIpMap.remove(duplicated);
            }
            LOG.info("refresh: Ip Interface loaded");
        } catch (Exception e){
            LOG.error("Loading Ip Interface failed: {}", e.getMessage(), e);
        } finally {
            vcontext.stop();
        }

        vcontext = m_loadSnmpInterfacesTimer.time();
        try {
            for (OnmsSnmpInterface snmp: m_snmpInterfaceDao.findAll()) {
                // Index the SNMP interfaces by node id
                final int nodeId = snmp.getNode().getId();
                List<OnmsSnmpInterface> snmpinterfaces = m_nodeToOnmsSnmpMap.get(nodeId);
                if (snmpinterfaces == null) {
                    snmpinterfaces = new ArrayList<>();
                    m_nodeToOnmsSnmpMap.put(nodeId, snmpinterfaces);
                }
                snmpinterfaces.add(snmp);
            }
            LOG.info("refresh: Snmp Interface loaded");
        } catch (Exception e){
            LOG.error("Loading Snmp Interface failed: {}",e.getMessage(),e);
        } finally {
            vcontext.stop();
        }

        vcontext = m_loadIpNetToMediaTimer.time();
        try {
            Set<String> duplicatednodemac = new HashSet<String>();
            Map<String, Integer> mactonodemap = new HashMap<String, Integer>();
            for (IpNetToMedia ipnettomedia: m_ipNetToMediaDao.findAll()) {
                if (duplicatednodemac.contains(ipnettomedia.getPhysAddress())) {
                    LOG.debug("refresh: different nodeid found for ip: {} mac: {}. Skipping...",InetAddressUtils.str(ipnettomedia.getNetAddress()), ipnettomedia.getPhysAddress());
                    continue;
                }
                OnmsIpInterface ip = m_ipToOnmsIpMap.get(ipnettomedia.getNetAddress());
                if (ip == null) {
                    LOG.debug("refresh: no nodeid found for ip: {} mac: {}. Skipping...",InetAddressUtils.str(ipnettomedia.getNetAddress()), ipnettomedia.getPhysAddress());
                    continue;
                }
                if (mactonodemap.containsKey(ipnettomedia.getPhysAddress())) {
                    if (mactonodemap.get(ipnettomedia.getPhysAddress()).intValue() != ip.getNode().getId().intValue()) {
                        LOG.debug("refresh:: different nodeid found for ip: {} mac: {}. Skipping...",InetAddressUtils.str(ipnettomedia.getNetAddress()), ipnettomedia.getPhysAddress());
                        duplicatednodemac.add(ipnettomedia.getPhysAddress());
                        continue;
                    }
                }

                if (!m_macToOnmsIpMap.containsKey(ipnettomedia.getPhysAddress())) {
                    m_macToOnmsIpMap.put(ipnettomedia.getPhysAddress(), new ArrayList<OnmsIpInterface>());
                    mactonodemap.put(ipnettomedia.getPhysAddress(), ip.getNode().getId());
                }
                m_macToOnmsIpMap.get(ipnettomedia.getPhysAddress()).add(ip);
            }
            for (String dupmac: duplicatednodemac) {
                m_macToOnmsIpMap.remove(dupmac);
            }
            LOG.info("refresh: IpNetToMedia loaded");
        } catch (Exception e){
            LOG.error("Loading ipNetToMedia failed: {}",e.getMessage(),e);
        } finally {
            vcontext.stop();
        }

        vcontext = m_loadVerticesTimer.time();
        try {
            loadVertices();
            LOG.info("refresh: Loaded Vertices");
        } catch (Exception e){
            LOG.error("Exception Loading Vertices: {}",e.getMessage(),e);
        } finally {
            vcontext.stop();
        }
        
        vcontext = m_loadEdgesTimer.time();
        try {
            loadEdges();
            LOG.info("refresh: Loaded Edges");
        } catch (Exception e){
            LOG.error("Exception Loading Edges: {}",e.getMessage(),e);
        } finally {
            vcontext.stop();
        }
    }

    @Override
    public void refresh() {
        final Timer.Context context = m_loadFullTimer.time();
        try {
            resetContainer();
            m_nodeMap = new HashMap<Integer, OnmsNode>();
            m_nodeToOnmsIpMap = new HashMap<Integer,  List<OnmsIpInterface>>();
            m_nodeToOnmsSnmpMap = new HashMap<Integer, List<OnmsSnmpInterface>>();
            m_nodeToOnmsIpPrimaryMap = new HashMap<Integer, OnmsIpInterface>();
            m_macToOnmsIpMap = new HashMap<String, List<OnmsIpInterface>>();
            m_ipToOnmsIpMap = new HashMap<InetAddress,  OnmsIpInterface>();
            doRefresh();
        } finally {
            context.stop();
        }
        
        LOG.info("refresh: Found {} groups", getGroups().size());
        LOG.info("refresh: Found {} vertices", getVerticesWithoutGroups().size());
        LOG.info("refresh: Found {} edges", getEdges().size());
    }
}
