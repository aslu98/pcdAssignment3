package part2.messages.accept;

import java.io.Serializable;
import java.util.List;

public final class PlayerAcceptedMsg implements Serializable {
    private static final long serialVersionUID = 7L;
    private final List<Integer> currentPositions;

    public PlayerAcceptedMsg(final List<Integer> currentPositions) {
        this.currentPositions = currentPositions;
    }

    public List<Integer> getCurrentPositions() {
        return currentPositions;
    }
}
