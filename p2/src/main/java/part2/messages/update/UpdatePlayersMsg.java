package part2.messages.update;

import akka.actor.ActorRef;
import part2.puzzle.Tile;

import java.io.Serializable;
import java.util.List;

public final class UpdatePlayersMsg implements Serializable {
	private final List<ActorRef> players;

	public UpdatePlayersMsg(List<ActorRef> players){
		this.players = players;
	}
	
	public List<ActorRef> getPlayers(){
		return players;
	}
}
