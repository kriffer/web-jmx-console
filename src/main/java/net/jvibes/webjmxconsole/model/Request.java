package net.jvibes.webjmxconsole.model;

import lombok.*;

/**
 * @author Anton Kravets
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class Request {
    private String host;
    private int port;
    private String status;
    private String pid;
}
