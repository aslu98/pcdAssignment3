package part2.messages.update;
import akka.actor.ActorRef;
import part2.messages.MessageType;

import java.io.Serializable;
import java.util.List;


public final class AckMsg implements Serializable {
    private final Serializable prevMsg;
    private final MessageType msgType;

    public AckMsg(Serializable prevMsg, MessageType msgType){
        this.msgType = msgType;
        this.prevMsg = prevMsg;
    }

    public Serializable getPrevMsg(){
        return prevMsg;
    }

    public MessageType getMsgType() {
        return msgType;
    }
}
