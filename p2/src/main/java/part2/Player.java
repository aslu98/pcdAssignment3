package part2;

import akka.actor.*;
import part2.messages.accept.NewPlayerMsg;
import part2.messages.accept.NotifyNewPlayerToMsg;
import part2.messages.accept.PlayerAcceptedMsg;

import part2.messages.terminate.PlayerExitMsg;
import part2.messages.update.UpdateNextMsg;
import part2.messages.update.UpdatePlayersMsg;
import part2.messages.update.UpdateTilesMsg;
import part2.puzzle.PuzzleBoard;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Player extends AbstractActor{
	private PuzzleBoard board;
	private List<ActorRef> players;
	private int myIndex;

	@Override
	public void preStart() {
		this.players = new ArrayList<>();
		players.add(getSelf());
		this.initializeBoard();
		System.out.println(getSelf() + " initialized");
	}

	// Overriding postRestart to disable the call to preStart() after restarts
	@Override
	public void postRestart(Throwable reason){}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(NotifyNewPlayerToMsg.class, msg -> getContext()
						.actorSelection("akka://DistributedSystem@" + msg.getHost() + ":" + msg.getPort() + "/user/*")
						.tell(new NewPlayerMsg(), getSelf()))
				.match(NewPlayerMsg.class, msg -> {
					this.addToPlayers(getSender());
					this.tellNextPlayer(new UpdatePlayersMsg(this.players));
					getSender().tell(new PlayerAcceptedMsg(this.board.getCurrentPositions()), getSelf());
				})
				.match(PlayerAcceptedMsg.class, msg -> board.setCurrentPositions(msg.getCurrentPositions()))
				.match(UpdateTilesMsg.class, msg -> {
					System.out.println("update tiles from " + getSender());
					if (!board.getCurrentPositions().equals(msg.getCurrentPositions())){
						System.out.println("update: different positions");
						board.setCurrentPositions(msg.getCurrentPositions());
						this.tellNextPlayer(new UpdateTilesMsg(msg.getCurrentPositions()));
					}
				})
				.match(UpdateNextMsg.class, msg -> this.tellNextPlayer(new UpdateTilesMsg(this.board.getCurrentPositions())))
				.match(UpdatePlayersMsg.class, msg -> {
					System.out.println("update players from " + getSender());
					if (!players.equals(msg.getPlayers())){
						System.out.println("update: different players: " + msg.getPlayers());
						this.players = msg.getPlayers();
						this.myIndex = players.indexOf(getSelf());
						this.tellNextPlayer(new UpdatePlayersMsg(msg.getPlayers()));
					}
				})
				.match(PlayerExitMsg.class, msg -> {
					System.out.println(getSender() + "failed");
					this.players.remove(getSender());
					this.tellNextPlayer(new UpdatePlayersMsg(this.players));
				})
				.match(Terminated.class, t -> {
					System.out.println(t.actor() + " terminated");
				})
				.build();
	}

	private void initializeBoard(){
		final int n = 3;
		final int m = 5;
		final String imagePath = "src/main/resources/bletchley-park-mansion.jpg";
		this.board = new PuzzleBoard(n, m, imagePath, getSelf());
		this.board.setVisible(true);
	}

	private void addToPlayers(final ActorRef newPlayer){
		getContext().watch(newPlayer);
		System.out.println("watching " + newPlayer + "from addToPlayers");
		final int playerIndex = (myIndex + 1) % players.size();
		if (playerIndex == 0){
			this.players.add(newPlayer);
		} else {
			this.players.add(playerIndex, newPlayer);
		}
	}

	private void tellNextPlayer(final Serializable message){
		ActorRef nextPlayer = getNextPlayer();
		if (nextPlayer != getSelf()){
			nextPlayer.tell(message, getSelf());
		}
	}

	private ActorRef getNextPlayer(){
		return players.get((myIndex + 1) % players.size());
	}

	@Override
	public void postStop() throws Exception {
		System.out.println("post stop");
		//nel caso il nextplayer fosse morto il mex che questo è morto non arriverebbe agli altri, quindi meglio mandarlo a tutti
		this.tellNextPlayer(new PlayerExitMsg());

		super.postStop();
	}
}
