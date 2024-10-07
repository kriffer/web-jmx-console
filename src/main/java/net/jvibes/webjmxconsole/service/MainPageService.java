package net.jvibes.webjmxconsole.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jvibes.webjmxconsole.model.ConnectionProperties;
import net.jvibes.webjmxconsole.utils.LocalEnvUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Anton Kravets
 */

@Service
@AllArgsConstructor
@Slf4j
public class MainPageService {

    private LocalEnvUtil localEnvUtil;
    private ConnectionProperties connectionProperties;

    public List<Map<String, String>> getRemoteConnections() {
        return connectionProperties.getConnection();
    }

    public HashMap<Long, String> getLocalProcesses() {
        List<ProcessHandle> processes = localEnvUtil.getProcesses();
        HashMap<Long, String> localProcesses = new HashMap<>();
        log.debug("Getting list of local java processes:");
        for (ProcessHandle handle : processes) {
            log.debug("PID:{},{}", handle.pid(), handle.info().commandLine().orElse("N/A"));
            String s = handle.info().commandLine().orElse("N/A");
            String[] cmd = s.split(" ");
            localProcesses.put(handle.pid(), cmd[cmd.length - 1]);
        }
        return localProcesses;
    }
}
