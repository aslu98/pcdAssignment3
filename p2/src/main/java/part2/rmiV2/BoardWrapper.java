package part2.rmiV2;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface BoardWrapper extends Remote {
    void updateBoard(final List<Integer> positions) throws RemoteException;
    void updateState(final List<Integer> positions) throws RemoteException;
    List<Integer> getPositions() throws RemoteException;
    List<BoardWrapper> getAllBoards() throws  RemoteException;
    void updateAllBoards(final List<BoardWrapper> others) throws RemoteException;
}
