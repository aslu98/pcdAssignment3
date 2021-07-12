package lab09.actors.hello_typed;

import akka.actor.typed.ActorSystem;

public class Main {
  public static void main(String[] args) throws Exception  {

	  final ActorSystem<BaseMsg> system =
			    ActorSystem.create(HappyActorBehaviour.create(), "hello");

		system.tell(new HelloMsg("World"));
		system.tell(new HelloMsg("Akka"));
	  }
}
