/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2009-2015 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2015 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.poller.monitors;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import org.apache.commons.codec.digest.DigestUtils;
import org.opennms.core.utils.ParameterMap;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.DistributionContext;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.PollStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



// This might actually be usable in the remote poller with some work
@Distributable(DistributionContext.DAEMON)

/**
 * <P>
 * This <code>ServiceMonitor</code> is designed to enable the evaluation
 * or execution of user-supplied scripts via the Java Scripting API (JSR-223)
 * Scripts should indicate a status whose string value is one of:
 * 
 * "OK" (service is available),
 * "UNK" (service status unknown),
 * "UNR" (service is unresponsive), or
 * "NOK" (service is unavailable).
 * 
 * These strings map into the status values defined in @PollStatus and are
 * indicated differently depending on the run-type of the script in question
 * (see below for details).
 *
 * Options: 
 * 
 * The script to be executed lives on the file system. If the scripting engine
 * supports it, scripts can be:
 * 
 * a) 'compile': script file is read from the file system and compiled before 
 *                evaluation at every polling interval (default)
 * b) 'pre-compile': script file is read from the file system and compiled
 *                    once during first poll, then the cached pre-compiled code
 *                    evaluated for each subsequent polling interval.
 * c) 'invoke-method': script file is read from the file system and compiled 
 *                     at every polling interval, but it need not have a main
 *                     method (implied or explicit) and a specific method is 
 *                     invoked instead. The parameter 'method-name' specifies
 *                     which method to run and the parameter 'method-args' is
 *                     a comma separated list of string arguments to supply
 *                     to the method.
 * 
 * Note that the specific script engine in use may not support options b and/or c.
 * 
 * In each case the script should populate the binding variable 'results'
 * having at least the 'status' key defined (as described above) and optionally
 * the 'reason' key. Populating the 'times' binding variable is also best
 * practice. If not defined the total script execution time is used instead.  
 *    
 * The following variables are declared in the script's execution binding:
 * 
 * map: A @Map<String,Object> allowing direct access to the list of parameters
 *      configured for the service at hand
 * ip_addr: A @String representing the IPv4 or IPv6 address of the interface
 *          on which the polled service resides
 * node_id: An int containing the unique identifying number from the OpenNMS
 *          configuration database of the node on whose interface the
 *          monitored service resides
 * node_label: A @String containing the textual node label of the node on whose
 *             interface the monitored service resides
 * svc_name: A @String containing the textual name of the monitored service
 * jsr223_monitor: The singleton instance of the @Jsr223Monitor class, useful
 *                 primarily for purposes of logging via its log(String sev,
 *                 String fmt, Object... args) method.  The severity must be
 *                 one of TRACE, DEBUG, INFO, WARN, ERROR, FATAL.  The format
 *                 is a printf-style format string, and the args fill in the
 *                 tokens.
 * results: A @HashMap<String,String> that the script may use to pass its results
 *          back to the @Jsr223Monitor. A status indication should be set into the
 *          entry with key "status", and for status indications other than "OK"
 *          a reason code should be set into the entry with key "reason".
 * times: A @LinkedHashMap<String,Number> that the script may use to pass one
 *        or more response times back to the @Jsr223Monitor.
 * </P>
 *
 * @author <A HREF="mailto:dschlenk@converge-one.com</A>
 * @author <A HREF="http://www.opennms.org">OpenNMS</A>
 */

public class Jsr223Monitor extends AbstractServiceMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(Jsr223Monitor.class);

    private static final String STATUS_UNKNOWN = "UNK";
    private static final String STATUS_UNRESPONSIVE = "UNR";
    private static final String STATUS_AVAILABLE = "OK";
    private static final String STATUS_UNAVAILABLE = "NOK";
    private static final String RUN_TYPE_COMPILE = "compile";
    private static final String RUN_TYPE_PRE_COMPILE = "pre-compile";
    private static final String RUN_TYPE_INVOKE_METHOD = "invoke-method";
    
    private ScriptEngineManager m_Manager;
    
    public class ScriptIdentity implements Serializable{
        private static final long serialVersionUID = 1L;
        private String fileName;
        private String languageName;
        private String md5sum;
        
        public ScriptIdentity(String fileName, String languageName,
                String md5sum) {
            super();
            this.fileName = fileName;
            this.languageName = languageName;
            this.md5sum = md5sum;
        }
        
        public String getFileName() {
            return fileName;
        }
        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
        public String getLanguageName() {
            return languageName;
        }
        public void setLanguageName(String languageName) {
            this.languageName = languageName;
        }
        public String getMd5sum() {
            return md5sum;
        }
        public void setMd5sum(String md5sum) {
            this.md5sum = md5sum;
        }
    }
    private Map<ScriptIdentity, CompiledScript> scriptCache = new HashMap<ScriptIdentity, CompiledScript>();
    
    /**
     * Initialize ScriptEngineManager
     */
    @Override
    public void initialize(Map<String, Object> parameters) {
        m_Manager = new ScriptEngineManager();
    }

    private ScriptEngine getScriptEngine(String fileName, String langName, String langMimeType){
        ScriptEngine ret = null;
        if(langName != null){
            ret = m_Manager.getEngineByName(langName);
        }
        if(ret == null && langMimeType != null){
            ret = m_Manager.getEngineByMimeType(langMimeType);
        }
        if(ret == null && fileName != null && fileName.contains(".")) {
            ret = m_Manager.getEngineByExtension(fileName.substring(fileName.lastIndexOf(".")));
        }
        
        return ret;
    }
    
    private synchronized PollStatus executeScript(MonitoredService svc, Map<String, Object> map) {
        PollStatus pollStatus = PollStatus.unavailable();
        
        String fileName = ParameterMap.getKeyedString(map,"file-name", null);
        String langName = ParameterMap.getKeyedString(map, "lang-name", null);
        String langMimeType = ParameterMap.getKeyedString(map, "script-mime-type", null);
        String methodName = ParameterMap.getKeyedString(map, "method-name", null);
        String methodArgs = ParameterMap.getKeyedString(map, "method-args", "");
        ScriptEngine engine = getScriptEngine(fileName, langName, langMimeType);
        
        String runType = ParameterMap.getKeyedString(map, "run-type", "compile");
        Path file = Paths.get(fileName);

        try {
            if(Files.exists(file) && Files.isReadable(file)){
                
                
                HashMap<String,String> results = new HashMap<String,String>();
                LinkedHashMap<String,Number> times = new LinkedHashMap<String,Number>();
                // Declare some beans that can be used inside the script
                // We do this in a separate Bindings object so that we don't pollute 
                // each ScriptEngine instance's ScriptContext. 
                Bindings bindings = new SimpleBindings();
                bindings.put("map", map);
                bindings.put("ip_addr",  svc.getIpAddr());
                bindings.put("node_id",svc.getNodeId());
                bindings.put("node_label", svc.getNodeLabel());
                bindings.put("svc_name", svc.getSvcName());
                bindings.put("jsr223_monitor", this);
                bindings.put("results", results);
                bindings.put("times", times);
                // Also add every key/value pair in map to the script context for the lazy 
                for (final Entry<String, Object> entry : map.entrySet()) {
                    bindings.put(entry.getKey(), entry.getValue());
                }
                    
                pollStatus = PollStatus.unknown("The script did not update the service status");

                long startTime = System.currentTimeMillis();
                if (RUN_TYPE_INVOKE_METHOD.equals(runType)
                        && engine instanceof Invocable) {
                    String[] args = null;
                    if (methodArgs.contains(",")) {
                        args = methodArgs.split(",");
                    } else {
                        args = new String[] { methodArgs };
                    }

                    LOG.debug("ScriptEngine {} supports 'invoke-method'. Evaluating method {} in script {}.",
                              engine.getClass(), methodName, fileName);
                    engine.eval(Files.newBufferedReader(file,
                                                        StandardCharsets.UTF_8),
                                bindings);
                    // redefine start time so we don't count the initial script compilation time etc
                    startTime = System.currentTimeMillis();
                    if (!"".equals(args)) {
                        ((Invocable) engine).invokeFunction(methodName,
                                                            (Object[]) args);
                    } else {
                        ((Invocable) engine).invokeFunction(methodName);
                    }
                } else if (RUN_TYPE_PRE_COMPILE.equals(runType)
                        && engine instanceof Compilable) {
                    LOG.debug("ScriptEngine {} supports 'pre-compile'. Evaluating script {}.",
                              engine.getClass(), fileName);
                    // TODO: make checking for script changes optional?
                    // Generate identity object:
                    ScriptIdentity si = new ScriptIdentity(
                                                           fileName,
                                                           engine.getFactory().getLanguageName(),
                                                           DigestUtils.md2Hex(Files.readAllBytes(file)));
                    CompiledScript cs = scriptCache.get(si);
                    if (cs == null) {
                        cs = ((Compilable) engine).compile(Files.newBufferedReader(file,
                                                                                   StandardCharsets.UTF_8));
                        scriptCache.put(si, cs);
                    }
                    // redefine start time so we don't count computing the MD5 sum or reading in the file etc
                    startTime = System.currentTimeMillis();
                    cs.eval(bindings);
                    // TODO: clean out the ScriptContext
                } else {
                    if (!RUN_TYPE_COMPILE.equals(runType)) {
                        LOG.warn("ScriptEngine {} does not support run-type {}; 'compile' run-type in use.",
                                 engine.getClass(), runType);
                    }
                    LOG.debug("Evaluating script {}.", fileName);
                    final Reader scriptReader = Files.newBufferedReader(file,
                                                                        StandardCharsets.UTF_8);
                    engine.eval(scriptReader, bindings);
                }
                long endTime = System.currentTimeMillis();
                if (!times.containsKey("response-time")) {
                    times.put("response-time", endTime - startTime);
                }

                if (STATUS_UNKNOWN.equals(results.get("status"))) {
                    pollStatus = PollStatus.unknown(results.get("reason"));
                } else if (STATUS_UNRESPONSIVE.equals(results.get("status"))) {
                    pollStatus = PollStatus.unresponsive(results.get("reason"));
                } else if (STATUS_AVAILABLE.equals(results.get("status"))) {
                    pollStatus = PollStatus.available();
                } else if (STATUS_UNAVAILABLE.equals(results.get("status"))) {
                    pollStatus = PollStatus.unavailable(results.get("reason"));
                } else {
                    pollStatus = PollStatus.unavailable(results.get("status"));
                }

                LOG.debug("Setting {} times for service '{}'", times.size(),
                          svc.getSvcName());
                pollStatus.setProperties(times);
            } else {
                LOG.warn("Cannot locate or read script file '{}'. Marking service '{}' down.",
                         fileName, svc.getSvcName());
                pollStatus = PollStatus.unavailable("Cannot locate or read script file: "
                        + fileName);
            }

        } catch (FileNotFoundException e) {
            LOG.warn("Could not find script file '{}'. Marking service '{}' down.",
                     fileName, svc.getSvcName());
            pollStatus = PollStatus.unavailable("Could not find script file: "
                    + fileName);
        } catch (IOException e) {
            pollStatus = PollStatus.unavailable(e.getMessage());
            LOG.warn("Jsr223Monitor poll for service '{}' failed with IOException: {}",
                     svc.getSvcName(), e.getMessage(), e);
        } catch (Throwable e) {
            // Catch any RuntimeException throws
            pollStatus = PollStatus.unavailable(e.getMessage());
            LOG.warn("Jsr223Monitor poll for service '{}' failed with unexpected throwable: {}",
                     svc.getSvcName(), e.getMessage(), e);
        }
        return pollStatus;
    }
    
    /** {@inheritDoc} */
    @Override
    public PollStatus poll(MonitoredService svc, Map<String,Object> map) {
        return executeScript(svc, map);
    }
    
    public void log(String level, String format, Object... args) {
        if ("TRACE".equals(level)) LOG.trace(format, args);
        if ("DEBUG".equals(level)) LOG.debug(format, args);
        if ("INFO".equals(level)) LOG.info(format, args);
        if ("WARN".equals(level)) LOG.warn(format, args);
        if ("ERROR".equals(level)) LOG.error(format, args);
        if ("FATAL".equals(level)) LOG.error(format, args);
    }
}
