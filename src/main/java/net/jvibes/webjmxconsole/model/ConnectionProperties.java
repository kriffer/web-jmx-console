package net.jvibes.webjmxconsole.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author Anton Kravets
 */

@Component
@ConfigurationProperties(prefix = "app.data")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConnectionProperties {
    List<Map <String, String>> connection;
}
