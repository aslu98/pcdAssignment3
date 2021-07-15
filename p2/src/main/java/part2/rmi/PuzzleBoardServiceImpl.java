package part2.rmi;

import part2.rmi.puzzle.PuzzleBoard;

import java.rmi.RemoteException;
import java.util.List;

class PlayerStateServiceImpl implements PlayerStateService{

    private List<Integer> positions;

    PlayerStateServiceImpl(final List<Integer> positions){
        this.positions = positions;
    }

    @Override
    public List<Integer> getPositions() throws RemoteException {
        return positions;
    }

    @Override
    public void updatePositions(List<Integer> positions) throws RemoteException {
         this.positions = positions;
    }
}
