import java.io.File;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import akka.actor.ActorSystem;
import akka.actor.Props;

public class Main{

  public static void main(String[] args) {
	/*Config config = ConfigFactory.parseFile(new File("src/main/java/part2/player.conf"));
	ActorSystem system = ActorSystem.create("MySystem",config);
	system.actorOf(Props.create(Pinger.class),"pinger");*/
	  Player p = new Player();
	  p.hello()
  }
}
