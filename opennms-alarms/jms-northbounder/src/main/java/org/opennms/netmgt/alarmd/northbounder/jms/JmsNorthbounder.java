/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2015 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.alarmd.northbounder.jms;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.commons.lang.StringUtils;
import org.opennms.core.spring.BeanUtils;
import org.opennms.core.utils.PropertiesUtils;
import org.opennms.netmgt.alarmd.api.NorthboundAlarm;
import org.opennms.netmgt.alarmd.api.NorthboundAlarm.AlarmType;
import org.opennms.netmgt.alarmd.api.NorthboundAlarm.x733ProbableCause;
import org.opennms.netmgt.alarmd.api.NorthbounderException;
import org.opennms.netmgt.alarmd.api.support.AbstractNorthbounder;
import org.opennms.netmgt.alarmd.northbounder.jms.JmsDestination.DestinationType;
import org.opennms.netmgt.dao.api.NodeDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;


/**
 * Northbound Interface JMS Implementation
 * 
 * Allows alarms to be automatically forwarded to a JMS destination. JMS
 * implementation neutral, defaults to ActiveMQ. To change,
 * add a Spring bean that implements javax.jms.ConnectionFactory
 * and change OpenNMS property opennms.alarms.northbound.jms.connectionFactoryImplRef
 * to that bean's ID. It will be wrapped in the Spring's CachingConnectionFactory
 * so your bean does not need to handle caching or pooling itself.
 *
 * Configuration is done in $ONMS_HOME/etc/jms-northbounder-configuration.xml
 * and is similar to the syslog NBI config file. See JmsNorthbounderConfig
 * or the appropriate schema file for details.
 * 
 * @author <a href="mailto:david@opennms.org">David Hustace</a>
 * @author <a href="mailto:dschlenk@converge-one.com">David Schlenk</a>
 * @version $Id: $
 */
public class JmsNorthbounder extends AbstractNorthbounder implements
        InitializingBean {

    protected JmsNorthbounder(String name) {
        super(name);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected boolean accepts(NorthboundAlarm alarm) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void forwardAlarms(List<NorthboundAlarm> alarms)
            throws NorthbounderException {
        // TODO Auto-generated method stub
        
    }
    /*private static final Logger LOG = LoggerFactory.getLogger(JmsNorthbounder.class);

    public static final String NBI_NAME = "JmsNorthbounder";

    private ConnectionFactory m_jmsNorthbounderConnectionFactory;

    private JmsNorthbounderConfig m_config;
    
    private NodeDao m_nodeDao;
    
    private JmsDestination m_jmsDestination;

    private JmsTemplate m_template;

    public JmsNorthbounder(JmsNorthbounderConfig config,
            ConnectionFactory jmsNorthbounderConnectionFactory,
            JmsDestination destination) {
        super(NBI_NAME + ":" + destination);
        m_config = config;
        m_jmsNorthbounderConnectionFactory = jmsNorthbounderConnectionFactory;
        m_jmsDestination = destination;
    }

    protected JmsNorthbounder() {
        super(NBI_NAME);
    }

    @Override
    public boolean accepts(NorthboundAlarm alarm) {
        if (!m_config.isEnabled()) {
            return false;
        }
        LOG.debug("Validating UEI of alarm: {}", alarm.getUei());

        if (getConfig().getUeis() == null
                || getConfig().getUeis().contains(alarm.getUei())) {
            LOG.debug("UEI: {}, accepted.", alarm.getUei());
            return true;
        }

        LOG.debug("UEI: {}, rejected.", alarm.getUei());
        return false;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        BeanUtils.assertAutowiring(this);
        LOG.debug("creating new JmsTemplate with connection to "
                + m_jmsNorthbounderConnectionFactory);
        m_template = new JmsTemplate(m_jmsNorthbounderConnectionFactory);
        if (m_jmsDestination.getDestinationType().equals(DestinationType.TOPIC)) {
            m_template.setPubSubDomain(true);
        }
        setNaglesDelay(m_config.getNaglesDelay());
        setMaxBatchSize(m_config.getBatchSize());
        setMaxPreservedAlarms(m_config.getQueueSize());
    }

    public void forwardAlarms(List<NorthboundAlarm> alarms)
            throws NorthbounderException {
        if (m_jmsDestination.isSendAsObjectMessageEnabled()) {
            for (NorthboundAlarm alarm : alarms) {
                m_template.convertAndSend(alarm);
            }
        } else {
            Map<Integer, Map<String, Object>> alarmMappings = new HashMap<Integer, Map<String, Object>>();
            for (final NorthboundAlarm alarm : alarms) {
                LOG.debug("Attempting to send a message to "
                        + m_jmsDestination.getJmsDestination() + " of type "
                        + m_jmsDestination.getDestinationType());
                try {
                    m_template.send(m_jmsDestination.getJmsDestination(),
                                    new MessageCreator() {

                                        @Override
                                        public Message createMessage(
                                                Session session)
                                                throws JMSException {
                                            return session.createTextMessage(convertAlarmToText(alarmMappings,
                                                                                                alarm));
                                        }

                                    });
                } catch (JmsException e) {
                    LOG.error("Unable to send alarm to JMS NB because "
                            + e.getLocalizedMessage());

                }
                LOG.debug("Sent message.");
            }
        }
    }
    
    private String convertAlarmToText(
            Map<Integer, Map<String, Object>> alarmMappings,
            NorthboundAlarm alarm) {

        String alarmXml = null;
        Map<String, Object> mapping = null;
        if (alarmMappings != null) {
            mapping = alarmMappings.get(alarm.getId());
        }

        if (mapping == null) {
            mapping = createMapping(alarmMappings, alarm);
        }

        LOG.debug("Making substitutions for tokens in message format for alarm: {}.",
                  alarm.getId());
        if (m_jmsDestination.getMessageFormat() != null) {
            alarmXml = PropertiesUtils.substitute(m_jmsDestination.getMessageFormat(),
                                                  mapping);
        } else {
            alarmXml = PropertiesUtils.substitute(m_config.getMessageFormat(),
                                                  mapping);
        }
        return alarmXml;

    }

    protected Map<String, Object> createMapping(
            Map<Integer, Map<String, Object>> alarmMappings,
            NorthboundAlarm alarm) {
        Map<String, Object> mapping;
        mapping = new HashMap<String, Object>();
        mapping.put("ackUser", alarm.getAckUser());
        mapping.put("appDn", alarm.getAppDn());
        mapping.put("logMsg", alarm.getLogMsg());
        mapping.put("objectInstance", alarm.getObjectInstance());
        mapping.put("objectType", alarm.getObjectType());
        mapping.put("ossKey", alarm.getOssKey());
        mapping.put("ossState", alarm.getOssState());
        mapping.put("ticketId", alarm.getTicketId());
        mapping.put("alarmUei", alarm.getUei());
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
        mapping.put("ipAddr", nullSafeToString(alarm.getIpAddr(), ""));
        mapping.put("lastOccurrence",
                    nullSafeToString(alarm.getLastOccurrence(), ""));

        if (alarm.getNodeId() != null) {
            LOG.debug("Adding nodeId: " + alarm.getNodeId().toString());
            mapping.put("nodeId", alarm.getNodeId().toString());
            String nodeLabel = getNodeDao().getLabelForId(alarm.getNodeId());
            mapping.put("nodeLabel", nodeLabel == null ? "?" : nodeLabel);
        } else {
            mapping.put("nodeId", "");
            mapping.put("nodeLabel", "");
        }

        String poller = alarm.getPoller() == null ? "localhost"
                                                 : alarm.getPoller().getId();
        mapping.put("distPoller", poller);

        String service = alarm.getService() == null ? "" : alarm.getService();
        mapping.put("ifService", service);

        mapping.put("severity", nullSafeToString(alarm.getSeverity(), ""));
        mapping.put("ticketState",
                    nullSafeToString(alarm.getTicketState(), ""));

        mapping.put("x733AlarmType", alarm.getX733Type());

        try {
            mapping.put("x733ProbableCause",
                        nullSafeToString(x733ProbableCause.get(alarm.getX733Cause()),
                                         ""));
        } catch (Exception e) {
            LOG.info("Exception caught setting X733 Cause: {}",
                     alarm.getX733Cause(), e);
            mapping.put("x733ProbableCause", "");
        }

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
        List<EventParm<?>> parmCollection = new LinkedList<EventParm<?>>();
        String parms = alarm.getEventParms();
        if (parms == null)
            return;

        char separator = ';';
        String[] parmArray = StringUtils.split(parms, separator);
        for (String string : parmArray) {

            char nameValueDelim = '=';
            String[] nameValueArray = StringUtils.split(string,
                                                        nameValueDelim);
            String parmName = nameValueArray[0];
            String parmValue = StringUtils.split(nameValueArray[1], '(')[0];

            EventParm<String> eventParm = new EventParm<String>(parmName,
                                                                parmValue);
            parmCollection.add(eventParm);
        }

        for (int i = 0; i < parmCollection.size(); i++) {
            EventParm<?> parm = parmCollection.get(i);
            Integer parmOffset = i + 1;
            mapping.put("parm[name-#" + parmOffset + "]", parm.getParmName());
            mapping.put("parm[#" + parmOffset + "]",
                        parm.getParmValue().toString());
            mapping.put("parm[" + parm.getParmName() + "]",
                        parm.getParmValue().toString());
        }
    }
    
    private static class EventParm<T extends Object> {
        private String m_parmName;
        private T m_parmValue;

        EventParm(String name, T value) {
            m_parmName = name;
            m_parmValue = value;
        }

        public String getParmName() {
            return m_parmName;
        }

        public T getParmValue() {
            return (T) m_parmValue;
        }
    }
    
    public JmsDestination getDestination() {
        return m_jmsDestination;
    }

    public JmsNorthbounderConfig getConfig() {
        return m_config;
    }

    public ConnectionFactory getJmsNorthbounderConnectionFactory() {
        return m_jmsNorthbounderConnectionFactory;
    }

    public void setJmsNorthbounderConnectionFactory(
            ConnectionFactory jmsNorthbounderConnectionFactory) {
        m_jmsNorthbounderConnectionFactory = jmsNorthbounderConnectionFactory;
    }

    public NodeDao getNodeDao() {
        return m_nodeDao;
    }

    public void setNodeDao(NodeDao nodeDao) {
        m_nodeDao = nodeDao;
    }
    */
}
