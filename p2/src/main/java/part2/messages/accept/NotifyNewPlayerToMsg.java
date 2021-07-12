package part2.messages.accept;

import java.io.Serializable;

public final class NotifyNewPlayerToMsg implements Serializable {
    private final String host;
    private final String port;

    public NotifyNewPlayerToMsg(String host, String port) {
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
