/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2014 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.alarmd.api.support;

import java.io.Serializable;
import java.io.StringWriter;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.opennms.netmgt.alarmd.api.NorthboundAlarm;
import org.opennms.netmgt.alarmd.api.NorthboundAlarm.AlarmType;
import org.opennms.netmgt.alarmd.api.NorthboundAlarm.x733ProbableCause;
import org.opennms.netmgt.alarmd.api.Northbounder;
import org.opennms.netmgt.alarmd.api.NorthbounderException;
import org.opennms.netmgt.dao.api.NodeDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * AbstractNorthBounder
 * 
 * The purpose of this class is manage the queue of alarms that need to be forward and receive queries to/from a Southbound Interface.
 * 
 * It passes Alarms on to the forwardAlarms method implemented by base classes in batches as they are 
 * added to the queue.  The forwardAlarms method does the actual work of sending them to the Southbound Interface.
 * 
 * preserve, accept and discard are called to add the Alarms to the queue as appropriate.  
 * 
 * @author <a mailto:david@opennms.org>David Hustace</a>
 */

public abstract class AbstractNorthbounder implements Northbounder, Runnable,
        StatusFactory<NorthboundAlarm> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNorthbounder.class);
    private final String m_name;
    private final AlarmQueue<NorthboundAlarm> m_queue;
    private JAXBContext m_jc;
    private Marshaller m_marshaller;
    protected NodeDao m_nodeDao;

    private volatile boolean m_stopped = true;

    private long m_retryInterval = 1000;

    protected AbstractNorthbounder(String name) {
        m_name = name;
        m_queue = new AlarmQueue<NorthboundAlarm>(this);
        initMarshaller();
    }

    private void initMarshaller() {
        try {
            m_jc = JAXBContext.newInstance(EventParms.class);
            m_marshaller = m_jc.createMarshaller();
            m_marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m_marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        } catch (JAXBException e) {
            LOG.error("Error initiallzing JAXB marshaller in thread {}", getName());
        }
    }

    public NodeDao getNodeDao() {
        return m_nodeDao;
    }

    public void setNodeDao(NodeDao nodeDao) {
        m_nodeDao = nodeDao;
    }

    @Override
    public String getName() {
        return m_name;
    }

    public void setNaglesDelay(long delay) {
        m_queue.setNaglesDelay(delay);
    }

    public void setRetryInterval(int retryInterval) {
        m_retryInterval = retryInterval;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        m_queue.setMaxBatchSize(maxBatchSize);
    }

    public void setMaxPreservedAlarms(int maxPreservedAlarms) {
        m_queue.setMaxPreservedAlarms(maxPreservedAlarms);
    }

    /** Override this to perform actions before startup. **/
    protected void onPreStart() {
    }

    /** Override this to perform actions after startup. **/
    protected void onPostStart() {
    }

    @Override
    public final void start() throws NorthbounderException {
        if (!m_stopped)
            return;
        this.onPreStart();
        m_stopped = false;
        m_queue.init();
        Thread thread = new Thread(this, getName() + "-Thread");
        thread.start();
        this.onPostStart();
    }

    @Override
    public final void onAlarm(NorthboundAlarm alarm)
            throws NorthbounderException {
        if (accepts(alarm)) {
            m_queue.accept(alarm);
        }
    };

    protected abstract boolean accepts(NorthboundAlarm alarm);

    protected void preserve(NorthboundAlarm alarm)
            throws NorthbounderException {
        m_queue.preserve(alarm);
    }

    protected void discard(NorthboundAlarm alarm)
            throws NorthbounderException {
        m_queue.discard(alarm);
    }

    /** Override this to perform actions when stopping. **/
    protected void onStop() {
    }

    @Override
    public final void stop() throws NorthbounderException {
        this.onStop();
        m_stopped = true;
    }

    @Override
    public void run() {

        try {

            while (!m_stopped) {

                List<NorthboundAlarm> alarmsToForward = m_queue.getAlarmsToForward();

                try {
                    forwardAlarms(alarmsToForward);
                    m_queue.forwardSuccessful(alarmsToForward);
                } catch (Exception e) {
                    m_queue.forwardFailed(alarmsToForward);
                    if (!m_stopped) {
                        // a failure occurred so sleep a moment and try again
                        Thread.sleep(m_retryInterval);
                    }
                }

            }

        } catch (InterruptedException e) {
            LOG.warn("Thread '{}' was interrupted unexpected.", getName());
        }

    }

    @Override
    public NorthboundAlarm createSyncLostMessage() {
        return NorthboundAlarm.SYNC_LOST_ALARM;
    }

    public abstract void forwardAlarms(List<NorthboundAlarm> alarms)
            throws NorthbounderException;

    protected Map<String, Object> createMapping(
            Map<Integer, Map<String, Object>> alarmMappings,
            NorthboundAlarm alarm) {
        Map<String, Object> mapping;
        mapping = new HashMap<String, Object>();
        mapping.put("ackUser", nullSafeToString(alarm.getAckUser(), ""));
        mapping.put("appDn", nullSafeToString(alarm.getAppDn(), ""));
        mapping.put("logMsg", nullSafeToString(alarm.getLogMsg(), ""));
        mapping.put("objectInstance", nullSafeToString(alarm.getObjectInstance(), ""));
        mapping.put("objectType", nullSafeToString(alarm.getObjectType(), ""));
        mapping.put("ossKey", nullSafeToString(alarm.getOssKey(), ""));
        mapping.put("ossState", nullSafeToString(alarm.getOssState(), ""));
        mapping.put("ticketId", nullSafeToString(alarm.getTicketId(), ""));
        mapping.put("ticketState", nullSafeToString(alarm.getTicketState(), ""));
        mapping.put("alarmUei", nullSafeToString(alarm.getUei(), ""));
        mapping.put("alarmKey", nullSafeToString(alarm.getAlarmKey(), ""));
        mapping.put("clearKey", nullSafeToString(alarm.getClearKey(), ""));
        mapping.put("description", nullSafeToString(alarm.getDesc(), ""));
        mapping.put("operInstruct", nullSafeToString(alarm.getOperInst(), ""));
        mapping.put("ackTime", nullSafeToString(alarm.getAckTime(), ""));

        AlarmType alarmType = alarm.getAlarmType() == null ? AlarmType.NOTIFICATION
                                                          : alarm.getAlarmType();
        mapping.put("alarmType", alarmType.name());

        String count = alarm.getCount() == null ? "1"
                                               : alarm.getCount().toString();
        mapping.put("count", count);

        mapping.put("firstOccurrence",
                    nullSafeToString(alarm.getFirstOccurrence(), ""));
        mapping.put("alarmId", alarm.getId().toString());
        mapping.put("ipAddr", nullSafeToString(alarm.getIpAddr().getHostAddress(), ""));
        mapping.put("lastOccurrence",
                    nullSafeToString(alarm.getLastOccurrence(), ""));

        if (alarm.getNodeId() != null) {
            LOG.debug("Adding nodeId: " + alarm.getNodeId().toString());
            mapping.put("nodeId", alarm.getNodeId().toString());
            String nodeLabel = m_nodeDao.getLabelForId(alarm.getNodeId());
            mapping.put("nodeLabel", nodeLabel == null ? "?" : nodeLabel);
        } else {
            mapping.put("nodeId", "");
            mapping.put("nodeLabel", "");
        }

        String poller = alarm.getPoller() == null ? "localhost"
                                                 : alarm.getPoller().getName();
        mapping.put("distPoller", poller);

        String service = alarm.getService() == null ? "" : alarm.getService();
        mapping.put("ifService", service);

        mapping.put("severity", nullSafeToString(alarm.getSeverity(), ""));
        mapping.put("ticketState",
                    nullSafeToString(alarm.getTicketState(), ""));

        mapping.put("x733AlarmType", nullSafeToString(alarm.getX733Type(), ""));

        try {
            mapping.put("x733ProbableCause",
                        nullSafeToString(x733ProbableCause.get(alarm.getX733Cause()),
                                         ""));
        } catch (Exception e) {
            LOG.info("Exception caught setting X733 Cause: {}",
                     alarm.getX733Cause(), e);
            mapping.put("x733ProbableCause", nullSafeToString(x733ProbableCause.other, ""));
        }

        // Get all event parms as a string
        mapping.put("eventParms", nullSafeToString(alarm.getEventParms(), ""));
        // Get all event parms serialized to XML
        buildParmMappingXml(alarm, mapping);
        // Do individual event mappings
        buildParmMappings(alarm, mapping);
        alarmMappings.put(alarm.getId(), mapping);
        return mapping;
    }

    private String nullSafeToString(Object obj, String defaultString) {
        if (obj != null) {
            defaultString = obj.toString();
        }
        return defaultString;
    }

    private void buildParmMappings(final NorthboundAlarm alarm,
            final Map<String, Object> mapping) {
        String parms = alarm.getEventParms();
        if (parms != null)
            return;
        EventParms eventParms = new EventParms(parms);
        List<EventParm> parmCollection = eventParms.getEventParm();

        for (int i = 0; i < parmCollection.size(); i++) {
            EventParm parm = parmCollection.get(i);
            Integer parmOffset = i + 1;
            mapping.put("parm[name-#" + parmOffset + "]", parm.getParmName());
            mapping.put("parm[#" + parmOffset + "]",
                        parm.getParmValue().toString());
            mapping.put("parm[" + parm.getParmName() + "]",
                        parm.getParmValue().toString());
        }
    }

    private void buildParmMappingXml(final NorthboundAlarm alarm,
            final Map<String, Object> mapping) {
        // Unlike the above method, we want an empty element to replace the
        // mapping even if there aren't any parms.
        StringWriter sw = new StringWriter();
        String parms = alarm.getEventParms();
        if (parms != null) {
            EventParms eventParms = new EventParms(parms);
            try {
                if (m_marshaller == null) {
                    initMarshaller();
                }
                JAXBElement<EventParms> rootElement = new JAXBElement<EventParms>(new QName("eventParms"), EventParms.class, eventParms);
                m_marshaller.marshal(rootElement, sw);
            } catch (JAXBException e) {
                LOG.error("Error marshalling event params to XML for alarm ID: {}", alarm.getId(), e);
            }
        }
        mapping.put("eventParmsXml", sw.toString());
    }

    private static class EventParms implements Serializable {

        private List<EventParm> m_eventParm = new ArrayList<EventParm>();

        public EventParms(String eventParms){
            if (eventParms == null || !eventParms.contains(";"))
                return;

            String[] parmArray = StringUtils.split(eventParms, ";");
            for (String string : parmArray) {
                if (!string.contains("="))
                    continue;
                String[] nameValueArray = StringUtils.split(string, "=");
                if ((nameValueArray.length < 2)
                        || (!nameValueArray[1].contains("(")))
                    continue;
                String parmName = nameValueArray[0];
                String parmValue = StringUtils.split(nameValueArray[1], "(")[0];

                EventParm eventParm =
                        new EventParm(nameValueArray[0],
                                StringUtils.split(nameValueArray[1], "(")[0]);
                m_eventParm.add(eventParm);
            }
        }
        
        public List<EventParm> getEventParm(){
            return m_eventParm;
        }
        public void setEventParm(List<EventParm> parms){
            m_eventParm = parms;
        }
    }

    private static class EventParm implements Serializable {
        private String m_parmName;
        private String m_parmValue;

        EventParm(String name, String value) {
            m_parmName = name;
            m_parmValue = value;
        }

        public String getParmName() {
            return m_parmName;
        }
        public void setParmName(String parmName){
            m_parmName = parmName;
        }
        public String getParmValue() {
            return m_parmValue;
        }
        public void setParmValue(String parmValue){
            m_parmValue = parmValue;
        }
    }

}
