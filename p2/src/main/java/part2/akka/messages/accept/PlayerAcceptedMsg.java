package part2.akka.messages.accept;

import akka.actor.ActorRef;
import part2.akka.messages.UniqueIdMsg;
import java.util.List;

public final class PlayerAcceptedMsg extends UniqueIdMsg {
    private static final long serialVersionUID = 7L;
    private final List<Integer> currentPositions;
    private final List<ActorRef> currentPlayers;

    public PlayerAcceptedMsg(final List<Integer> currentPositions, final List<ActorRef> currentPlayers) {
        this.currentPositions = currentPositions;
        this.currentPlayers = currentPlayers;
    }

    public List<Integer> getCurrentPositions() {
        return currentPositions;
    }

    public List<ActorRef> getCurrentPlayers() {
        return currentPlayers;
    }
}
