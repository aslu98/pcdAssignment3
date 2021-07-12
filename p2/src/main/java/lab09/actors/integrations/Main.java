package lab09.actors.integrations;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

public class Main {
  public static void main(String[] args) throws Exception  {
    ActorSystem system = ActorSystem.create("MySystem");
    
    ActorRef act = system.actorOf(Props.create(ViewActor.class));
	new ViewFrame(act).display();
  }
}
