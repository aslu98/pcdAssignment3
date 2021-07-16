package part2.rmiV1;

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
    public synchronized void updatePositions(List<Integer> positions) throws RemoteException {
         this.positions = positions;
         this.myPlayer.updateFromPlayerState(positions);
    }
}
