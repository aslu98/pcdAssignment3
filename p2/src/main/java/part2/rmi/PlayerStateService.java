package part2.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface PlayerStateService extends Remote {

    public List<Integer> getPositions() throws RemoteException;

    public void updatePositions(final List<Integer> positions) throws RemoteException;
}
