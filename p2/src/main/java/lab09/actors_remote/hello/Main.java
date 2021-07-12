package lab09.actors_remote.hello;

import java.io.File;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

public class Main {
  public static void main(String[] args) throws Exception{

	  Config config = ConfigFactory.parseFile(new File("src/main/java/lab09/actors_remote/hello/app1.conf"));
	  ActorSystem system = ActorSystem.create("MySystem",config);
	  
	  ActorRef act = system.actorOf(Props.create(HappyActor.class),"myActor");
	  System.out.println(act.path());
	  act.tell(new HelloMsg("Ba"), ActorRef.noSender());
	  Thread.sleep(10000000);
  }
}
