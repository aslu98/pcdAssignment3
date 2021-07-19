package part2.rmiV1;

import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;

class PlayerStateServiceImpl implements PlayerStateService{

    private List<Integer> positions;
    private final Player myPlayer;
    private Long lastMoment;

    PlayerStateServiceImpl(final List<Integer> positions, final Player myPlayer){
        this.positions = positions;
        this.myPlayer = myPlayer;
        this.lastMoment = new Date().getTime();
    }

    @Override
    public List<Integer> getPositions() throws RemoteException {
        return positions;
    }

    @Override
    public synchronized void updatePositions(List<Integer> positions, final Long moment) throws RemoteException {
        if (moment > this.lastMoment){
            this.positions = positions;
            this.myPlayer.updateFromPlayerState(positions);
            this.lastMoment = moment;
        }
    }
}
