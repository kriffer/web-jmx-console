package net.jvibes.webjmxconsole.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;


import jakarta.websocket.OnMessage;
import lombok.extern.slf4j.Slf4j;
import net.jvibes.webjmxconsole.model.ClientWorker;
import net.jvibes.webjmxconsole.model.Request;
import net.jvibes.webjmxconsole.service.Client;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import java.io.EOFException;
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.SocketException;
import java.rmi.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Anton Kravets
 */


@Service
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {


    Map<String, String> systemProperties = new HashMap<>();
    private final ConcurrentHashMap<String, ClientWorker> clientWorkers = new ConcurrentHashMap<>();

    RuntimeMXBean runtimeData = null;

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectWriter objectWriter;
    private boolean isDisconnect = false;

    public WebSocketHandler(ObjectMapper objectMapper) {
        this.objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable throwable) {
        log.error("error occured at sender " + session, throwable);
    }

//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
//        log.info(String.format("Session %s closed because of %s", session.getId(), status.toString()));
//        sessions.remove(session.getId());
//
//
//
//    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String clientId = getClientId(session);

        ClientWorker worker = clientWorkers.remove(clientId);
        if (worker != null) {
            worker.shutdown();
            worker.getClient().close();
            log.info("Client worker shutdown: {}", clientId);
        }

        sessions.remove(clientId);
        log.info("Client disconnected: {}", clientId);
    }


    private String getClientId(WebSocketSession session) {
        // Use a unique identifier, e.g., query parameter or session ID
        return session.getId();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {


        String clientId = getClientId(session);
        sessions.put(clientId, session);

        Client client = new Client();
        ClientWorker worker = new ClientWorker(session, client);
        clientWorkers.put(clientId, worker);
        worker.start();


        log.info("Client connected: {}", clientId);
    }

    @Override
    @OnMessage
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException, InterruptedException {

        String clientId = getClientId(session);
        ClientWorker worker = clientWorkers.get(clientId);

        if (worker == null) {
            log.error("No worker found for client: {}", clientId);
            return;
        }
        log.debug("Client message {}", message.getPayload());


        worker.submit(() -> {

            try {
                String localPid = "";
                String host = "";
                int port = 0;
                var clientMessage = message.getPayload();
                ObjectMapper mapper = new ObjectMapper();
                Request request = mapper.readValue(clientMessage, Request.class);
                host = request.getHost();
                port = request.getPort();

                String status = request.getStatus();
                localPid = request.getPid();

                if (status != null && !status.isEmpty()) {
                    if (status.equals("DISCONNECT")) {
                        isDisconnect = true;
                        session.close();
                        localPid = "";

                        worker.getClient().close();
                    } else {
                        isDisconnect = false;
                    }

                }


                getUpdatedData(session, worker.getClient(), host, port, localPid);


            } catch (Exception ex) {

                String formattedRootError = getFormattedRootError(ex);
                log.error("Error inside client thread: {}", formattedRootError);

                HashMap<String, String> errorMap = new HashMap();
                errorMap.put("status", "ERROR");
                errorMap.put("message", formattedRootError);
                TextMessage errorMessage;
                try {
                    errorMessage = new TextMessage(objectWriter.writeValueAsString(errorMap));
                    session.sendMessage(errorMessage);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                ex.printStackTrace();
            }
        });


    }

    public static String getFormattedRootError(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.format("[%s] %s", cause.getClass().getName(), cause.getMessage());
    }

    private void getUpdatedData(WebSocketSession session, Client client, String host, int port, String localPid) throws MalformedObjectNameException, IOException, InstanceNotFoundException, InterruptedException {
        if ((host != null && !host.isEmpty()) && (port > 0)) {

            client.connect(host, port, localPid);
            HashMap<String, String> statusMap = new HashMap();
            statusMap.put("status", "OK");
            TextMessage response = new TextMessage(objectWriter.writeValueAsString(statusMap));
            session.sendMessage(response);
        }
        if (localPid != null && !localPid.isEmpty()) {


            client.connect(host, port, localPid);
            HashMap<String, String> statusMap = new HashMap();
            statusMap.put("status", "OK");
            TextMessage response = new TextMessage(objectWriter.writeValueAsString(statusMap));
            session.sendMessage(response);
        }

        client.initRuntimeData();
        client.initOSData();
        client.initClassData();
        client.initThreadData();
        client.initDirectBufferPoolData();
        client.initMappedBufferPoolData();
        runtimeData = client.getRuntimeData();
        systemProperties = runtimeData.getSystemProperties();

        if (systemProperties.get("java.vm.name").startsWith("Zing")) {
            client.initZingHeapData();
            client.initHeapData();
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
        int availableProcessors = client.getOSData().getAvailableProcessors();
        osMap.put("osName", osName);
        osMap.put("osVersion", osVersion);
        osMap.put("osArch", osArch);
        osMap.put("availableProcessors", String.valueOf(availableProcessors));
        TextMessage textMessage2 = new TextMessage(objectWriter.writeValueAsString(osMap));
        session.sendMessage(textMessage2);
        osMap.clear();


        while (session.isOpen() && !Thread.currentThread().isInterrupted()) {
            try {

                HashMap<String, String> tempMap = new HashMap();

                if (systemProperties.get("java.vm.name").startsWith("Zing")) {
                    com.azul.zing.management.MemoryUsage heapData = client.getZingHeapData();
                    com.azul.zing.management.MemoryUsage zingNonHeapData = client.getZingNonHeapData();
                    //getting max heap (Xmx value) for Zing ELASTIC heap using common JMX beans API
                    MemoryUsage heapDataCommon = client.getHeapData();

                    long used = heapData.getUsed();
                    long committed = heapData.getSize();
                    long max = heapDataCommon.getMax();
                    long init = heapData.getInitialReserved();
                    String poolSizeTypeName = heapData.getMemoryPoolSizeType().name();
                    long nonHeapInit = zingNonHeapData.getInitialReserved();
                    long nonHeapUsed = zingNonHeapData.getUsed();
                    long nonHeapSize = zingNonHeapData.getSize();
                    tempMap.put("used", String.valueOf(used));
                    tempMap.put("poolSizeTypeName", poolSizeTypeName);
                    tempMap.put("committed", String.valueOf(committed));
                    tempMap.put("init", String.valueOf(init));
                    tempMap.put("max", String.valueOf(max));
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


                BufferPoolMXBean directBufferPoolData = client.getDirectBufferPoolData();
                BufferPoolMXBean mappedBufferPoolData = client.getMappedBufferPoolData();

                tempMap.put("directPoolName", directBufferPoolData.getName());
                tempMap.put("directPoolCount", String.valueOf(directBufferPoolData.getCount()));
                tempMap.put("directPoolMemoryUsed", String.valueOf(directBufferPoolData.getMemoryUsed()));
                tempMap.put("directPoolTotalCapacity", String.valueOf(directBufferPoolData.getTotalCapacity()));


                tempMap.put("mappedPoolName", mappedBufferPoolData.getName());
                tempMap.put("mappedPoolCount", String.valueOf(mappedBufferPoolData.getCount()));
                tempMap.put("mappedPoolMemoryUsed", String.valueOf(mappedBufferPoolData.getMemoryUsed()));
                tempMap.put("mappedPoolTotalCapacity", String.valueOf(mappedBufferPoolData.getTotalCapacity()));

                double processCpuLoad = client.getOSData().getProcessCpuLoad();
                if (!javaVersion.startsWith("1.8")) {
                    long totalMemorySize = client.getOSData().getTotalMemorySize();
                    tempMap.put("totalMemorySize", String.valueOf(totalMemorySize));
                    long freeMemorySize = client.getOSData().getFreeMemorySize();
                    tempMap.put("freeMemorySize", String.valueOf(freeMemorySize));

                }

                long totalSwapSpaceSize = client.getOSData().getTotalSwapSpaceSize();
                long freeSwapSpaceSize = client.getOSData().getFreeSwapSpaceSize();
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
                if (isDisconnect) {
                    client.close();


                }

            } catch (IOException | MalformedObjectNameException | InstanceNotFoundException |
                     InterruptedException e) {
                log.error("Error: {}", e.getMessage());
                client.close();
                session.close();
                break;
            }
        }
    }
}
