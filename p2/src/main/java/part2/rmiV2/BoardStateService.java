package part2.rmiV2;

import part2.rmiV2.puzzle.PuzzleBoard;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface BoardStateService extends Remote {

    List<Integer> getPositions() throws RemoteException;
    void update(final List<Integer> positions, BoardWrapper source) throws RemoteException;
    void registerBoard(BoardWrapper board) throws RemoteException;
}
