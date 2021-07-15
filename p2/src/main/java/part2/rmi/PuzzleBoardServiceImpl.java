package part2.rmi;

import part2.rmi.puzzle.PuzzleBoard;

import java.rmi.RemoteException;
import java.util.List;

class PlayerStateServiceImpl implements PlayerStateService{

    private List<Integer> positions;
    private final Player myPlayer;

    PlayerStateServiceImpl(final List<Integer> positions, final Player myPlayer){
        this.positions = positions;
        this.myPlayer = myPlayer;
    }

    @Override
    public List<Integer> getPositions() throws RemoteException {
        return positions;
    }

    @Override
    public void updatePositions(List<Integer> positions) throws RemoteException {
         this.positions = positions;
         this.myPlayer.updateFromPlayerState(positions);
    }
}
