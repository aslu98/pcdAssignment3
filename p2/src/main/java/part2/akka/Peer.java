package part2.akka;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import part2.akka.messages.accept.NotifyNewPlayerMsg;

import java.io.File;

public class Peer {
	final ActorSystem system;
	final ActorRef player;

	public Peer(final String hostname, final int port){
		Config standardConfig = ConfigFactory.parseFile(new File("src/main/java/part2/player.conf"));
		Config hostnameConfig =  ConfigFactory.parseString("akka.remote.artery.canonical.hostname=" + hostname)
				.withFallback(standardConfig);
		Config config = ConfigFactory.parseString("akka.remote.artery.canonical.port=" + port)
				.withFallback(hostnameConfig);
		system = ActorSystem.create("DistributedSystem",config);
		player = system.actorOf(Props.create(Player.class),"player");
		System.out.println("You can connect to the player on port " + port + ".");
	}

	public Peer(final int port){
		this("127.0.0.1", port);
	}

	public void notifyPlayer(final String host, final String port){
		player.tell(new NotifyNewPlayerMsg(host,port), ActorRef.noSender());
	}
}
