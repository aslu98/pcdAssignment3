package part2.rmiV2;

import part2.rmiV2.puzzle.PuzzleBoard;

import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BoardWrapperImpl implements BoardWrapper{
    private List<BoardWrapper> others;
    private final PuzzleBoard board;
    private final Player myPlayer;

    public BoardWrapperImpl(final int n, final int m, final String imagePath, final Player myPlayer){
        this.board = new PuzzleBoard(n, m, imagePath, this);
        this.others = new CopyOnWriteArrayList<>();
        this.others.add(this);
        this.board.setVisible(true);
        this.myPlayer = myPlayer;
    }
    @Override
    public void updateBoard(List<Integer> positions) throws RemoteException {
        this.board.setCurrentPositions(positions);
    }

    @Override
    public void updateAllBoards(List<BoardWrapper> others) throws RemoteException {
        this.others = others;
        this.others.add(this);
    }

    @Override
    public void updateState(List<Integer> positions) throws RemoteException {
        this.myPlayer.update(positions);
    }

    @Override
    public List<Integer> getPositions() throws RemoteException {
        return this.board.getCurrentPositions();
    }

    @Override
    public List<BoardWrapper> getAllBoards() throws RemoteException {
        return this.others;
    }
}
