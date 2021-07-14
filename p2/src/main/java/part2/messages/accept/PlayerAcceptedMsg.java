package part2.messages.accept;

import part2.messages.UniqueIdMsg;
import java.util.List;

public final class PlayerAcceptedMsg extends UniqueIdMsg {
    private static final long serialVersionUID = 7L;
    private final List<Integer> currentPositions;

    public PlayerAcceptedMsg(final List<Integer> currentPositions) {
        this.currentPositions = currentPositions;
    }

    public List<Integer> getCurrentPositions() {
        return currentPositions;
    }
}
