package lab09.actors.hello_typed;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class HappyActorBehaviour extends AbstractBehavior<BaseMsg> {

	public static Behavior<BaseMsg> create() {
		return Behaviors.setup(HappyActorBehaviour::new);
	}

	private HappyActorBehaviour(ActorContext<BaseMsg> context) {
		super(context);
	}

	@Override
	public Receive<BaseMsg> createReceive() {
		return newReceiveBuilder().onMessage(HelloMsg.class, this::onHelloMsg).build();
	}

	private Behavior<BaseMsg> onHelloMsg(HelloMsg msg) {
		System.out.println("Hello " + msg.getContent());
		return this;
	}

}
