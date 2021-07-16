package part2.rmiV2;

import java.rmi.server.UnicastRemoteObject;

public class Main {

    public static void main(String args[]) {
        int n = 3;
        int m = 5;
        String imagePath = "src/main/resources/bletchley-park-mansion.jpg";
        Player p = (args.length < 1) ? new Player(n, m, imagePath) : new Player(n, m, imagePath, args[0]);
    }
}