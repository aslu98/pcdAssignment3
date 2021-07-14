package part2.messages.ack;
import part2.messages.UniqueIdMsg;

import java.io.Serializable;


public final class AckMsg implements Serializable {
    private static final long serialVersionUID = 5L;
    private final UniqueIdMsg prevMsg;

    public AckMsg(UniqueIdMsg prevMsg){
        this.prevMsg = prevMsg;
    }

    public UniqueIdMsg getPrevMsg(){
        return prevMsg;
    }
}
