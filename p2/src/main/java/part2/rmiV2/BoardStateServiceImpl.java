package part2.rmiV2;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.util.List;

class BoardStateServiceImpl implements BoardStateService {

    private List<Integer> positions;
    private final List<BoardWrapper> boards;

    BoardStateServiceImpl(final BoardWrapper board) throws RemoteException {
        this.positions = board.getPositions();
        this.boards = board.getAllBoards();
        this.updateLocalBoards();
    }

    @Override
    public synchronized List<Integer> getPositions() throws RemoteException {
        return positions;
    }

    @Override
    public synchronized void update(List<Integer> positions, BoardWrapper source) throws RemoteException {
        this.positions = positions;
        for (BoardWrapper b: boards) {
            if (b.hashCode() != source.hashCode()){
                try {
                    b.updateBoard(positions);
                } catch (RemoteException ex){
                    this.boards.remove(b);
                    this.updateLocalBoards();
                }
            }
        }
    }

    @Override
    public synchronized void registerBoard(BoardWrapper board) throws RemoteException {
        this.boards.add(board);
        this.updateLocalBoards();
        board.updateBoard(positions);
    }

    private void updateLocalBoards() throws RemoteException {
        for (BoardWrapper b: boards) {
            try {
                b.updateAllBoards(boards);
            } catch (ConnectException ex){
                this.boards.remove(b);
            }
        }
    }

}
