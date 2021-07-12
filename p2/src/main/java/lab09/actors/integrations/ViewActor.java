package lab09.actors.integrations;

import akka.actor.AbstractActor;

public class ViewActor extends AbstractActor {
	@Override
	public Receive createReceive() {
		return receiveBuilder().match(PressedMsg.class, msg -> {
			System.out.println("Pressed!");
		}).build();
	}
}
