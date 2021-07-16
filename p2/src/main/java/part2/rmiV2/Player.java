package part2.rmiV2;
import lab10.rmi.HelloService;
import part2.rmiV1.PlayerStateService;

import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class Player {

    private Registry registry;
    private BoardWrapper board;
    private BoardStateService boardStateObj;

    public Player(final int n, final int m, final String imagePath) {
        this(n, m, imagePath, null);
    }

    public Player(final int n, final int m, final String imagePath, String host) {
        try {
            this.registry = LocateRegistry.getRegistry(host);
            this.initializeBoard(n, m ,imagePath);
            this.connectToBoardState();
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private void initializeBoard(final int n, final int m, final String imagePath) throws RemoteException {
        this.board = new BoardWrapperImpl(n, m, imagePath, this);
        UnicastRemoteObject.exportObject(board, 0);
    }

    private void connectToBoardState() throws RemoteException {
        try {
            this.boardStateObj = (BoardStateService) registry.lookup("BoardState");
            System.out.println(boardStateObj.getPositions());
            boardStateObj.registerBoard(board);
            System.out.println("BoardState already exists. New board registered.");
        } catch (RemoteException | NotBoundException ex) {
            System.out.println("BoardState did not existed, creating new one.");
            this.boardStateObj = new BoardStateServiceImpl(this.board);
            BoardStateService boardStateStub = (BoardStateService) UnicastRemoteObject.exportObject(this.boardStateObj, 0);
            registry.rebind("BoardState", boardStateStub);
            System.out.println(boardStateObj.getPositions());
            System.out.println("BoardState created.");
        }
    }

    public void update(final List<Integer> positions) throws RemoteException {
        try {
            System.out.println("UPDATE: BoardState exists, updated.");
            this.boardStateObj.update(positions, this.board);
        } catch (RemoteException ex) {
            System.out.println("UPDATE: BoardState not found, trying to connect.");
            this.connectToBoardState();
        }
    }
}