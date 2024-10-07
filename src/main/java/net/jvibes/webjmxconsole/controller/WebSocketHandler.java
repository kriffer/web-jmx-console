package net.jvibes.webjmxconsole.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;


import jakarta.websocket.OnMessage;
import lombok.extern.slf4j.Slf4j;
import net.jvibes.webjmxconsole.model.Request;
import net.jvibes.webjmxconsole.service.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import java.io.EOFException;
import java.io.IOException;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Anton Kravets
 */


@Service
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private Client client;

    private Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectWriter objectWriter;
    public volatile static boolean isDisconnect = false;

    public WebSocketHandler(ObjectMapper objectMapper) {
        this.objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable throwable) {
        log.error("error occured at sender " + session, throwable);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info(String.format("Session %s closed because of %s", session.getId(), status.toString()));
        sessions.remove(session.getId());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Connected ... " + session.getId());
        sessions.put(session.getId(), session);
    }

    @Override
    @OnMessage
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        log.debug("Client message {}", message.getPayload());
        var clientMessage = message.getPayload();
        ObjectMapper mapper = new ObjectMapper();
        Request request = mapper.readValue(clientMessage, Request.class);
        String host = request.getHost();
        int port = request.getPort();
        String localPid = request.getPid();
        String status = request.getStatus();


        if (status != null && !status.isEmpty()) {
            if (status.equals("DISCONNECT")) {
                isDisconnect = true;
                localPid = "";
                client.pid = "";
                client.close();
                return;
            } else {
                isDisconnect = false;
            }

        }

        try {
            if ((host != null && !host.isEmpty()) && (port > 0)) {
                client.host = host;
                client.port = port;
                client.connect();
                HashMap<String, String> statusMap = new HashMap();
                statusMap.put("status", "OK");
                TextMessage response = new TextMessage(objectWriter.writeValueAsString(statusMap));
                session.sendMessage(response);
            }
            if (localPid != null && !localPid.isEmpty()) {

                client.pid = localPid;
                client.connect();
                HashMap<String, String> statusMap = new HashMap();
                statusMap.put("status", "OK");
                TextMessage response = new TextMessage(objectWriter.writeValueAsString(statusMap));
                session.sendMessage(response);
            }


            client.initRuntimeData();
            client.initOSData();
            client.initClassData();
            client.initThreadData();


            RuntimeMXBean runtimeData = client.getRuntimeData();

            Map<String, String> systemProperties = runtimeData.getSystemProperties();

            if (systemProperties.get("java.vm.name").startsWith("Zing")) {
                client.initZingHeapData();
            } else {
                client.initHeapData();
            }

            HashMap<String, String> hm = new HashMap();


            String name = runtimeData.getName();


            String inputArguments = runtimeData.getInputArguments().toString();
            String classPath = runtimeData.getClassPath();
            String libraryPath = runtimeData.getLibraryPath();
            String vmVendor = runtimeData.getVmVendor();
            String specVersion = runtimeData.getSpecVersion();

            String javaVersion = systemProperties.get("java.vm.specification.version");

            systemProperties.forEach((key, value) -> {
                log.debug("Prop {} : {}", key, value);
            });
            long startTime = runtimeData.getStartTime();
            if (!javaVersion.startsWith("1.8")) {
                long pid = runtimeData.getPid();
                hm.put("pid", String.valueOf(pid));
            }

            hm.put("name", name);
            if (systemProperties.get("jdk.vendor.version") != null) {
                hm.put("jdkVendorVersion", systemProperties.get("jdk.vendor.version"));
            }
            if (systemProperties.get("java.vendor.version") != null) {
                hm.put("jdkVendorVersion", systemProperties.get("java.vendor.version"));
            }
            hm.put("inputArguments", inputArguments);
            hm.put("classPath", classPath);
            hm.put("libraryPath", libraryPath);
            hm.put("vmName", systemProperties.get("java.vm.name"));
            hm.put("vmVendor", vmVendor);
            hm.put("vmVersion", systemProperties.get("java.runtime.version"));
            hm.put("specVersion", specVersion);
            hm.put("startTime", String.valueOf(startTime));

            TextMessage textMessage1 = new TextMessage(objectWriter.writeValueAsString(hm));
            session.sendMessage(textMessage1);

            HashMap<String, String> osMap = new HashMap();


            String osName = client.getOSData().getName();
            String osVersion = client.getOSData().getVersion();
            String osArch = client.getOSData().getArch();

            osMap.put("osName", osName);
            osMap.put("osVersion", osVersion);
            osMap.put("osArch", osArch);

            TextMessage textMessage2 = new TextMessage(objectWriter.writeValueAsString(osMap));
            session.sendMessage(textMessage2);
            osMap.clear();

            Thread loopThread = new Thread(() -> {
                while (!isDisconnect) {
                    try {

                        HashMap<String, String> tempMap = new HashMap();

                        if (systemProperties.get("java.vm.name").startsWith("Zing")) {
                            com.azul.zing.management.MemoryUsage heapData = client.getZingHeapData();
                            com.azul.zing.management.MemoryUsage zingNonHeapData = client.getZingNonHeapData();

                            long used = heapData.getUsed();
                            long committed = heapData.getSize();
                            long init = heapData.getInitialReserved();
                            long nonHeapInit = zingNonHeapData.getInitialReserved();
                            long nonHeapUsed = zingNonHeapData.getUsed();
                            long nonHeapSize = zingNonHeapData.getSize();
                            tempMap.put("used", String.valueOf(used));
                            tempMap.put("committed", String.valueOf(committed));
                            tempMap.put("init", String.valueOf(init));

                            tempMap.put("nonHeapInit", String.valueOf(nonHeapInit));
                            tempMap.put("nonHeapUsed", String.valueOf(nonHeapUsed));
                            tempMap.put("nonHeapSize", String.valueOf(nonHeapSize));

                        } else {

                            MemoryUsage heapData = client.getHeapData();
                            MemoryUsage nonHeapData = client.getNonHeapData();
                            long used = heapData.getUsed();
                            long committed = heapData.getCommitted();
                            long init = heapData.getInit();
                            long max = heapData.getMax();

                            long nonHeapInit = nonHeapData.getInit();
                            long nonHeapUsed = nonHeapData.getUsed();
                            long nonHeapSize = nonHeapData.getCommitted();
                            tempMap.put("used", String.valueOf(used));
                            tempMap.put("committed", String.valueOf(committed));
                            tempMap.put("init", String.valueOf(init));
                            tempMap.put("max", String.valueOf(max));
                            tempMap.put("nonHeapInit", String.valueOf(nonHeapInit));
                            tempMap.put("nonHeapUsed", String.valueOf(nonHeapUsed));
                            tempMap.put("nonHeapSize", String.valueOf(nonHeapSize));
                        }


                        double processCpuLoad = client.getOSData().getProcessCpuLoad();
                        if (!javaVersion.startsWith("1.8")) {
                            long totalMemorySize = client.getOSData().getTotalMemorySize();
                            tempMap.put("totalMemorySize", String.valueOf(totalMemorySize));
                            long freeMemorySize = client.getOSData().getFreeMemorySize();
                            tempMap.put("freeMemorySize", String.valueOf(freeMemorySize));

                        }

                        int availableProcessors = client.getOSData().getAvailableProcessors();
                        long totalSwapSpaceSize = client.getOSData().getTotalSwapSpaceSize();
                        long freeSwapSpaceSize = client.getOSData().getFreeSwapSpaceSize();
                        tempMap.put("availableProcessors", String.valueOf(availableProcessors));
                        tempMap.put("totalSwapSpaceSize", String.valueOf(totalSwapSpaceSize));
                        tempMap.put("freeSwapSpaceSize", String.valueOf(freeSwapSpaceSize));

                        double systemLoadAverage = client.getOSData().getSystemLoadAverage();
                        int threadCount = client.getThreadData().getThreadCount();
                        int loadedClassCount = client.getClassData().getLoadedClassCount();
                        long uptime = runtimeData.getUptime();
                        tempMap.put("uptime", String.valueOf(uptime));

                        tempMap.put("processCpuLoad", String.valueOf(processCpuLoad));

                        tempMap.put("systemLoadAverage", String.valueOf(systemLoadAverage));
                        tempMap.put("loadedClassCount", String.valueOf(loadedClassCount));
                        tempMap.put("threadCount", String.valueOf(threadCount));

                        TextMessage textMessage3 = new TextMessage(objectWriter.writeValueAsString(tempMap));

                        try {
                            if (session.isOpen()) {
                                log.debug("Sending message: " + textMessage3 + " with session:" + session.getId());
                                session.sendMessage(textMessage3);
                                log.debug("Message: " + textMessage3 + " IS SENT (session:" + session.getId() + ")");
                            }
                        } catch (IOException e) {
                            log.error("Broken pipe or I/O error: " + e.getMessage());
                            if (e instanceof SocketException) {
                                log.error("Socket exception: " + e.getMessage());
                            } else if (e instanceof EOFException) {
                                log.error("Connection closed by client: " + e.getMessage());
                            }
                            log.error("IO Error: {}", e.getMessage());

                        }
                        Thread.sleep(5000);
                    } catch (IOException | MalformedObjectNameException | InstanceNotFoundException |
                             InterruptedException e) {
                        log.error("Error: {}", e.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

            loopThread.start();

        } catch (Exception ex) {
            log.error("Error: {}", ex.getMessage());
            HashMap<String, String> statusMap = new HashMap();
            statusMap.put("status", "FAILED");
            statusMap.put("error", ex.getMessage());
            TextMessage response = new TextMessage(objectWriter.writeValueAsString(statusMap));
            session.sendMessage(response);
            log.debug("Message: " + response + " IS SENT (session:" + session.getId() + ")");
            statusMap.clear();

        }
    }
}
