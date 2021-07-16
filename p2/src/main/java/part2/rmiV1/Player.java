package part2.rmiV1;
import part2.rmiV1.exceptions.DuplicateIdException;
import part2.rmiV1.puzzle.PuzzleBoard;

import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class Player {

    private static final int MAX_ID = 3;
    private final int n;
    private final int m;
    private int id = 0;
    private boolean idFound = false;
    private Registry registry;
    private PuzzleBoard board;
    private PlayerStateService playerObj;
    private PlayerStateService playerStub;
    private List<PlayerStateService> otherPlayersObjs;

    public Player(final int n, final int m, final String imagePath) {
        this(n, m, imagePath, null);
    }

    public Player(final int n, final int m, final String imagePath, String host) {
        this.n = n;
        this.m = m;
        try {
            this.registry = LocateRegistry.getRegistry(host);
            this.board = new PuzzleBoard(n, m, imagePath, this);
            this.board.setVisible(true);
            this.initializeService();
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private synchronized void setId() throws RemoteException {
        while (!idFound){
            id = id + 1;
            if (id > MAX_ID){
                System.out.println("Too many connected players, the maximum number is " + MAX_ID);
                System.exit(0);
            }
            try {
                PlayerStateService lookupObj = (PlayerStateService) registry.lookup("player" + id);
                lookupObj.getPositions();
            } catch (NotBoundException | ConnectException ex) {
                idFound = true;
            }
        }
    }

    private void registerService() throws RemoteException {
        this.playerObj = new PlayerStateServiceImpl(board.getCurrentPositions(), this);
        this.playerStub  = (PlayerStateService) UnicastRemoteObject.exportObject(playerObj, 0);
        registry.rebind("player" + id, playerStub);
        System.out.println("Object player " + id + " registered.");
    }

    private void discoverOtherPlayers() throws RemoteException {
        System.out.println("NEW DISCOVERY");
        this.otherPlayersObjs = new ArrayList<>();
        int searchId = 0;
        while (searchId < MAX_ID){
            searchId = searchId + 1;
            try {
                PlayerStateService otherPlayerObj = (PlayerStateService) registry.lookup("player" + searchId);
                otherPlayerObj.getPositions();
                if (searchId == id)
                    if (otherPlayerObj.hashCode() != this.playerStub.hashCode()){
                    throw new DuplicateIdException();
                } else {
                    System.out.println("Discovery: id " +  searchId + " found.");
                    this.otherPlayersObjs.add(otherPlayerObj);
                }
            } catch (NotBoundException | RemoteException ex) {
                System.out.println("Discovery: id " +  searchId + " not found.");
            } catch (DuplicateIdException ex){
                System.out.println("Another player is connected with the same id! Re-initializing your service.");
                this.initializeService();
            }
        }
    }

    private void initializeService() throws RemoteException {
        this.setId();
        this.registerService();
        this.discoverOtherPlayers();
        if (!this.otherPlayersObjs.isEmpty()){
            if (otherPlayersObjs.get(0).getPositions().size() != board.getCurrentPositions().size()){
                throw new IllegalArgumentException("Your board sizing is not compatible with the other players. " +
                        "\nThe correct sizing is " + n + "x" + m);
            }
            board.setCurrentPositions(otherPlayersObjs.get(0).getPositions());
        }

        for (PlayerStateService opState: otherPlayersObjs){
            System.out.println(opState.getPositions());
        }
    }

    public void updateFromPlayerState(final List<Integer> positions) throws RemoteException {
        this.board.setCurrentPositions(positions);
    }

    public void updateFromBoard(final List<Integer> positions) throws RemoteException {
        this.discoverOtherPlayers();
        for (PlayerStateService opState: otherPlayersObjs){
            opState.updatePositions(positions);
        }
        this.playerObj.updatePositions(positions);
    }
}