package part2.rmi;
import part2.rmi.puzzle.PuzzleBoard;

import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class Player {

    private static final int MAX_ID = 5;
    private int id = 0;
    private boolean idFound = false;
    private Registry registry;
    private PuzzleBoard board;
    private PlayerStateService playerObj;
    private List<PlayerStateService> otherPlayersObjs;

    public Player(final int n, final int m, final String imagePath) {
        this(n, m, imagePath, null);
    }

    public Player(final int n, final int m, final String imagePath, String host) {
        try {
            this.registry = LocateRegistry.getRegistry(host);
            this.setId();
            this.board = new PuzzleBoard(n, m , imagePath, this);
            this.board.setVisible(true);

            this.registerService();
            this.discoverOtherPlayers();
            for (PlayerStateService opState: otherPlayersObjs){
                System.out.println(opState.getPositions());
            }

            this.initializeBoard();
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private void setId() throws RemoteException {
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
        PlayerStateService playerObjStub  = (PlayerStateService) UnicastRemoteObject.exportObject(playerObj, 0);
        registry.rebind("player" + id, playerObjStub);
        System.out.println("Object player " + id + " registered.");
    }

    private void discoverOtherPlayers(){
        this.otherPlayersObjs = new ArrayList<>();
        int searchId = 0;
        while (searchId < MAX_ID){
            searchId = searchId + 1;
            if (searchId != id){
                try {
                    PlayerStateService otherPlayerObj = (PlayerStateService) registry.lookup("player" + searchId);
                    otherPlayerObj.getPositions();
                    this.otherPlayersObjs.add(otherPlayerObj);
                    System.out.println("Discovery: id " +  searchId + " found.");
                } catch (NotBoundException | RemoteException ex) {
                    System.out.println("Discovery: id " +  searchId + " not found.");
                }
            }
        }
    }

    private void initializeBoard() throws RemoteException {
        if (!this.otherPlayersObjs.isEmpty()){
            board.setCurrentPositions(otherPlayersObjs.get(0).getPositions());
        }
    }

    public void updateFromPlayerState(final List<Integer> positions){
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