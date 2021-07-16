package part2.rmiV2;

import part2.rmiV2.puzzle.PuzzleBoard;

import java.rmi.RemoteException;
import java.util.List;

public class BoardWrapperImpl implements BoardWrapper{
    private final PuzzleBoard board;
    private final Player myPlayer;

    public BoardWrapperImpl(final int n, final int m, final String imagePath, final Player myPlayer){
        this.board = new PuzzleBoard(n, m, imagePath, this);
        this.board.setVisible(true);
        this.myPlayer = myPlayer;
    }
    @Override
    public void updateBoard(List<Integer> positions) throws RemoteException {
        this.board.setCurrentPositions(positions);
        System.out.println("update state to board on boardwrapper");
    }

    public void updateState(List<Integer> positions) throws RemoteException {
        this.myPlayer.update(positions);
        System.out.println("update board to state on boardwrapper");
    }

    @Override
    public List<Integer> getPositions() throws RemoteException {
        return this.board.getCurrentPositions();
    }
}
