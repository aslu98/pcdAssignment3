package part2.akka.messages.terminate;

import akka.actor.ActorRef;
import part2.akka.messages.UniqueIdMsg;


public final class PlayerExitMsg extends UniqueIdMsg {
    private static final long serialVersionUID = 6L;
    private final ActorRef exitedActor;

    public PlayerExitMsg(ActorRef exitedActor) {
        this.exitedActor = exitedActor;
    }

    public ActorRef getExitedActor() {
        return exitedActor;
    }
}
