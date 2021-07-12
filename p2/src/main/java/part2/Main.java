package part2;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import part2.messages.accept.NewPlayerMsg;

import javax.management.openmbean.OpenDataException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Optional;

public class Main {
	public static void main(String[] args) throws Exception {
		Peer peer;
		final Optional<Integer> optPort = findFreePort();
			if (optPort.isPresent()){
				peer = new Peer(optPort.get());
			} else {
				throw new Exception("No ports available");
			}
		if (args.length != 0){
			peer.notifyPlayer("127.0.0.1", args[0]);
		}
	  }

  private static Optional<Integer> findFreePort(){
	  final InetSocketAddress randomSocketAddress = new InetSocketAddress(0);
	  Optional<Integer> port = Optional.empty();
	  try (ServerSocket ss = new ServerSocket()) {
		  ss.bind(randomSocketAddress);
		  port = Optional.of(ss.getLocalPort());
	  } catch (IOException e) {
		  e.printStackTrace();
	  }
	  return port;
  }
}
