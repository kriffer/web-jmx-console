package net.jvibes.webjmxconsole.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jvibes.webjmxconsole.service.MainPageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author Anton Kravets
 */

@Controller
@AllArgsConstructor
@Slf4j
public class MainController {

   private MainPageService mainPageService;

    @GetMapping("/")
    public String getMainPage(Model model) {
        model.addAttribute("connectionMap", mainPageService.getRemoteConnections());
        model.addAttribute("pids", mainPageService.getLocalProcesses());
        return "main";
    }

}
