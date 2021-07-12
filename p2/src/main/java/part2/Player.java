package part2;

import akka.actor.*;
import part2.messages.accept.NewPlayerMsg;
import part2.messages.accept.NotifyNewPlayerToMsg;
import part2.messages.accept.PlayerAcceptedMsg;

import part2.messages.update.AckMsg;
import part2.messages.update.UpdatePlayersMsg;
import part2.messages.update.UpdateTilesMsg;
import part2.puzzle.PuzzleBoard;
import part2.puzzle.SelectionManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Player extends AbstractActor{
	private PuzzleBoard board;
	private List<ActorRef> players;
	private int myIndex;

	@Override
	public void preStart() {
		System.out.println("call preStart");
		this.players = new ArrayList<>();
		players.add(getSelf());
		this.initializeBoard();
	}

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
				.match(PlayerAcceptedMsg.class, msg -> {
					board.setCurrentPositions(msg.getCurrentPositions());
				})
				.match(UpdateTilesMsg.class, msg -> {
					System.out.println("update msg");
					if (!board.getCurrentPositions().equals(msg.getCurrentPositions())){
						System.out.println("update different positions");
						board.setCurrentPositions(msg.getCurrentPositions());
						this.tellNextPlayer(new UpdateTilesMsg(msg.getCurrentPositions()));
						System.out.println(this.board.getCurrentPositions());
					} else if (getSender() == ActorRef.noSender()){
						System.out.println("no sender"); //in questo caso è stato mandato dalla PuzzleBoard!
						this.tellNextPlayer(new UpdateTilesMsg(msg.getCurrentPositions()));
					}
				})
				.match(UpdatePlayersMsg.class, msg -> {
					if (!players.equals(msg.getPlayers())){
						this.players = msg.getPlayers();
						this.myIndex = players.indexOf(getSelf());
						this.tellNextPlayer(new UpdatePlayersMsg(msg.getPlayers()));
					}
				})
				.match(AckMsg.class, msg -> System.out.println("ACK")).build();
	}

	protected void initializeBoard(){
		final int n = 3;
		final int m = 5;
		final String imagePath = "src/main/resources/bletchley-park-mansion.jpg";
		System.out.println("new board");
		this.board = new PuzzleBoard(n, m, imagePath, getSelf());
		this.board.setVisible(true);
	}

	private void addToPlayers(final ActorRef newPlayer){
		final int playerIndex = (myIndex + 1) % players.size();
		if (playerIndex == 0){
			this.players.add(newPlayer);
		} else {
			this.players.add(playerIndex, newPlayer);
		}
	}

	private void tellNextPlayer(final Serializable message){
		ActorRef nextPlayer = players.get((myIndex + 1) % players.size());
		if (nextPlayer != self()){
			nextPlayer.tell(message, getSelf());
		}
	}
}
