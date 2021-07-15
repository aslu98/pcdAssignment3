package part2.messages.terminate;

import akka.actor.ActorRef;
import part2.messages.UniqueIdMsg;


public final class PlayerFailMsg extends UniqueIdMsg {
    private static final long serialVersionUID = 10L;
    private final ActorRef failedActor;
    private final UniqueIdMsg msgToSend;

    public PlayerFailMsg(ActorRef failedActor, UniqueIdMsg msgToSend) {
        this.failedActor = failedActor;
        this.msgToSend = msgToSend;
    }

    public ActorRef getFailedActor() {
        return failedActor;
    }

    public UniqueIdMsg getMsgToSend() {
        return msgToSend;
    }
}
