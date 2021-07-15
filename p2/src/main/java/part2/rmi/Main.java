package part2.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

public class Main {

    public static void main(String args[]) {
        int n = 3;
        int m = 5;
        String imagePath = "src/main/resources/bletchley-park-mansion.jpg";
        Player p = (args.length < 1) ? new Player(n, m, imagePath) : new Player(n, m, imagePath, args[0]);
    }
}