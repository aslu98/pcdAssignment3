package part2.rmiV1;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface PlayerStateService extends Remote {

    List<Integer> getPositions() throws RemoteException;

    void updatePositions(final List<Integer> positions, final Long moment) throws RemoteException;
}
