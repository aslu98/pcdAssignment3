package part2.akka.messages.update;

import akka.actor.ActorRef;
import part2.akka.messages.UniqueIdMsg;

import java.util.List;

public final class UpdatePlayersMsg extends UniqueIdMsg {
	private static final long serialVersionUID = 1L;
	private final List<ActorRef> players;

	public UpdatePlayersMsg(final List<ActorRef> players){
		this.players = players;
	}
	
	public List<ActorRef> getPlayers(){
		return players;
	}
}
