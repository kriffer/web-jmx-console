package net.jvibes.webjmxconsole.service;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.*;

import com.sun.management.OperatingSystemMXBean;

/**
 * @author Anton Kravets
 */


@Component
@Slf4j
public class Client {

    public String host = "";
    public int port = 0;
    public String pid = "";

    private ClientListener listener;
    private MBeanServerConnection mbsc;
    private java.lang.management.MemoryMXBean mxbeanProxyHeap;
    private com.azul.zing.management.MemoryMXBean mxbeanProxyZingHeap;
    private JMXConnector jmxConnector;
    private RuntimeMXBean mxbeanProxyRuntime;
    private OperatingSystemMXBean mxbeanProxyOS;
    private ClassLoadingMXBean mxbeanProxyClassLoading;
    private ThreadMXBean mxbeanProxyThreading;

    public void connect() throws IOException {

        String jmxURL;
        if (pid != null && !pid.isEmpty()) {
            try {
                VirtualMachine vm = VirtualMachine.attach(pid);
                String javaVersion = vm.getSystemProperties().getProperty("java.version");
                log.debug("Java version: {} ", javaVersion);

                if (javaVersion.startsWith("1.8")) {
                    Runtime runtime = Runtime.getRuntime();
                    String command1 = "jcmd " + pid + " ManagementAgent.stop";
                    runtime.exec(command1);
                    sleep(1000);
                    String command2 = "jcmd " + pid + " ManagementAgent.start jmxremote.port=9999 jmxremote.authenticate=false jmxremote.ssl=false";
                    Process process = runtime.exec(command2);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("JCMD returns: {}", line);
                    }
                    sleep(1000);
                    jmxURL = "service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi";
                } else {
                    vm.startLocalManagementAgent();
                    jmxURL = vm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");
                }
                vm.detach();
            } catch (AttachNotSupportedException e) {
                throw new RuntimeException(e);
            }
        } else {
            jmxURL = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";
        }

        JMXServiceURL url = new JMXServiceURL(jmxURL);
        jmxConnector = JMXConnectorFactory.connect(url);
        this.listener = new ClientListener();
        echo("\nGet an MBeanServerConnection");
        this.mbsc = jmxConnector.getMBeanServerConnection();
    }


    public void initHeapData() {
        try {
            ObjectName mbeanName = new ObjectName("java.lang:type=Memory");
            this.mxbeanProxyHeap =
                    JMX.newMXBeanProxy(mbsc, mbeanName, java.lang.management.MemoryMXBean.class);
            echo("\nAdd notification listener for heap...");
            mbsc.addNotificationListener(mbeanName, listener, null, null);
        } catch (InstanceNotFoundException | MalformedObjectNameException | IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

    }

    public void initZingHeapData() throws IOException, MalformedObjectNameException, InstanceNotFoundException {
        ObjectName mbeanName = new ObjectName("com.azul.zing:type=Memory");
        this.mxbeanProxyZingHeap =
                JMX.newMXBeanProxy(mbsc, mbeanName, com.azul.zing.management.MemoryMXBean.class, true);
        echo("\nAdd notification listener for Zing heap...");
        mbsc.addNotificationListener(mbeanName, listener, null, null);

    }

    public void initOSData() throws MalformedObjectNameException {
        ObjectName mbeanName = new ObjectName("java.lang:type=OperatingSystem");
        this.mxbeanProxyOS =
                JMX.newMXBeanProxy(mbsc, mbeanName, OperatingSystemMXBean.class);
        echo("\nAdd notification listener for OS...");
    }

    public void initRuntimeData() throws MalformedObjectNameException {
        ObjectName mbeanName = new ObjectName("java.lang:type=Runtime");
        this.mxbeanProxyRuntime =
                JMX.newMXBeanProxy(mbsc, mbeanName, RuntimeMXBean.class);

        echo("\nAdd notification listener for runtime...");
    }

    public void initClassData() throws MalformedObjectNameException {
        ObjectName mbeanName = new ObjectName("java.lang:type=ClassLoading");
        this.mxbeanProxyClassLoading =
                JMX.newMXBeanProxy(mbsc, mbeanName, ClassLoadingMXBean.class);
        echo("\nAdd notification listener for ClassLoading...");
    }

    public void initThreadData() throws MalformedObjectNameException {
        ObjectName mbeanName = new ObjectName("java.lang:type=Threading");
        this.mxbeanProxyThreading =
                JMX.newMXBeanProxy(mbsc, mbeanName, ThreadMXBean.class);
        echo("\nAdd notification listener for Threading...");
    }

    public com.azul.zing.management.MemoryUsage getZingHeapData() throws IOException, MalformedObjectNameException, InstanceNotFoundException {
        return mxbeanProxyZingHeap.getJavaHeapMemoryUsage();
    }

    public com.azul.zing.management.MemoryUsage getZingNonHeapData() throws IOException, MalformedObjectNameException, InstanceNotFoundException {
        return mxbeanProxyZingHeap.getNonJavaHeapMemoryUsage();
    }

    public MemoryUsage getHeapData() throws IOException, MalformedObjectNameException, InstanceNotFoundException {
        return mxbeanProxyHeap.getHeapMemoryUsage();
    }

    public MemoryUsage getNonHeapData() {
        return mxbeanProxyHeap.getNonHeapMemoryUsage();
    }

    public RuntimeMXBean getRuntimeData() {
        return mxbeanProxyRuntime;
    }

    public OperatingSystemMXBean getOSData() throws IOException, MalformedObjectNameException, InstanceNotFoundException {
        return mxbeanProxyOS;
    }

    public ClassLoadingMXBean getClassData() throws IOException, MalformedObjectNameException, InstanceNotFoundException {
        return mxbeanProxyClassLoading;

    }

    public ThreadMXBean getThreadData() throws IOException, MalformedObjectNameException, InstanceNotFoundException {
        return mxbeanProxyThreading;

    }

    public static class ClientListener implements NotificationListener {
        public void handleNotification(Notification notification,
                                       Object handback) {
            echo("\nReceived notification:");
            echo("\tClassName: " + notification.getClass().getName());
            echo("\tSource: " + notification.getSource());
            echo("\tType: " + notification.getType());
            echo("\tMessage: " + notification.getMessage());
            if (notification instanceof AttributeChangeNotification) {
                AttributeChangeNotification acn =
                        (AttributeChangeNotification) notification;
                echo("\tAttributeName: " + acn.getAttributeName());
                echo("\tAttributeType: " + acn.getAttributeType());
                echo("\tNewValue: " + acn.getNewValue());
                echo("\tOldValue: " + acn.getOldValue());
            }
        }
    }

    public void close() throws IOException {
        jmxConnector.close();
        echo("\njmxConnector is closed...");
    }

    private static void echo(String msg) {
        log.info(msg);
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error("Error happened: {}", e.getMessage());
        }
    }
}
