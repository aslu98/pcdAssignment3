package lab09.actors_remote.hello;

import akka.actor.AbstractActor;

public class HappyActor extends AbstractActor {

	@Override
	public void preStart(){
		System.out.println(getSelf() + "prestart");
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(HelloMsg.class, msg -> {
	    		System.out.println("Hello "+msg.getContent());
		}).build();
	}
}
