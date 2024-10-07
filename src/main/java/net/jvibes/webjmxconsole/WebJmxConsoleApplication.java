package net.jvibes.webjmxconsole;

import net.jvibes.webjmxconsole.model.ConnectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author Anton Kravets
 */

@SpringBootApplication
@EnableConfigurationProperties(ConnectionProperties.class)
public class WebJmxConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebJmxConsoleApplication.class, args);
    }

}
