package net.jvibes.webjmxconsole.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jvibes.webjmxconsole.service.MainPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Anton Kravets
 */

@RestController
@AllArgsConstructor
@RequestMapping("/api")
@Slf4j
public class MainController {

    private MainPageService mainPageService;


    @GetMapping("/connections")
    public List<Map<String, String>> getConnections() {
        return Optional.ofNullable(mainPageService.getRemoteConnections())
                .orElse(Collections.emptyList());

    }

    @GetMapping("/pids")
    public Map<Long, String> getPids() {
        return Optional.ofNullable(mainPageService.getLocalProcesses())
                .orElse(Collections.emptyMap());
    }
}
