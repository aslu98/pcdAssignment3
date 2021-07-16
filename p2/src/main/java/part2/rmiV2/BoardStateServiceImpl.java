package part2.rmiV2;

import part2.rmiV2.puzzle.PuzzleBoard;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

class BoardStateServiceImpl implements BoardStateService {

    private List<Integer> positions;
    private final List<BoardWrapper> boards;

    BoardStateServiceImpl(final List<Integer> positions, final BoardWrapper board){
        this.positions = positions;
        this.boards = new ArrayList<>();
        this.boards.add(board);
    }

    @Override
    public synchronized List<Integer> getPositions() throws RemoteException {
        return positions;
    }

    @Override
    public synchronized void update(List<Integer> positions) throws RemoteException {
        this.positions = positions;
        for (BoardWrapper b: boards) {
            try {
                b.updateBoard(positions);
            } catch (RemoteException ex){
                this.boards.remove(b);
            }
        }
    }

    @Override
    public synchronized void registerBoard(BoardWrapper board) throws RemoteException {
        this.boards.add(board);
        board.updateBoard(positions);
    }
}
