package net.jvibes.webjmxconsole.utils;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Anton Kravets
 */


@Component
@AllArgsConstructor
public class LocalEnvUtil {
    public List<ProcessHandle> getProcesses() {
        return ProcessHandle.allProcesses()
                .filter(processHandle -> processHandle.info().command().isPresent() &&
                        processHandle.info().command().get().contains("java"))
                .collect(Collectors.toList());
    }
}
