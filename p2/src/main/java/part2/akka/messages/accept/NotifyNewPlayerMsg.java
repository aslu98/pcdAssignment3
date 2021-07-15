package part2.akka.messages.accept;

import java.io.Serializable;

public final class NotifyNewPlayerMsg implements Serializable {
    private static final long serialVersionUID = 8L;
    private final String host;
    private final String port;

    public NotifyNewPlayerMsg(String host, String port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }
}
