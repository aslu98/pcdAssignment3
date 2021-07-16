package part2.rmiV2;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class BoardStateServiceImpl implements BoardStateService {

    private List<Integer> positions;
    private final List<BoardWrapper> boards;

    BoardStateServiceImpl(final List<Integer> positions, final BoardWrapper board){
        this.positions = positions;
        this.boards = new CopyOnWriteArrayList<>();
        this.boards.add(board);
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
                }
            }
        }
    }

    @Override
    public synchronized void registerBoard(BoardWrapper board) throws RemoteException {
        this.boards.add(board);
        board.updateBoard(positions);
    }
}
