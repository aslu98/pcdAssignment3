package lab09.actors_remote.pingpong;
import akka.actor.*;

public class Pinger extends AbstractActor {

	 public void preStart() {
		  final ActorSelection ponger = getContext().actorSelection("akka://MySystem@127.0.0.1:26521/user/ponger");
		  ponger.tell(new PingMsg(0), getSelf());
	  }	
	 
	  @Override
		public Receive createReceive() {
			return receiveBuilder().match(PongMsg.class, msg -> {
				System.out.println("PONG received: "+  msg.getValue());
				getSender().tell(new PingMsg(msg.getValue() + 1), getSelf());
			}).build();
	  }
}
