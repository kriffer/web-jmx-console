package net.jvibes.webjmxconsole.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@AllArgsConstructor
@Slf4j
public class PageController {

     @GetMapping("/")
     public String getMainPage( ) {

         return "main";
     }

}
