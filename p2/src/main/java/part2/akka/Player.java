package part2.akka;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import part2.akka.messages.UniqueIdMsg;
import part2.akka.messages.accept.NewPlayerMsg;
import part2.akka.messages.accept.NotifyNewPlayerMsg;
import part2.akka.messages.accept.PlayerAcceptedMsg;
import part2.akka.messages.ack.AckMsg;
import part2.akka.messages.ack.SendReplayMsg;
import part2.akka.messages.terminate.PlayerExitMsg;
import part2.akka.messages.terminate.PlayerFailMsg;
import part2.akka.messages.update.UpdateNextMsg;
import part2.akka.messages.update.UpdatePlayersMsg;
import part2.akka.messages.update.UpdateTilesMsg;
import part2.akka.puzzle.PuzzleBoard;

import java.time.Duration;
import java.util.*;

public class Player extends AbstractActorWithTimers{
	private static final int MAX_ATTEMPTS = 5;
	private String timerKey;
	private PuzzleBoard board;
	private List<ActorRef> players;
	private Map<String, Integer> unackedMessages;

	@Override
	public void preStart() {
		this.timerKey = "periodic_tiles_update" + getSelf();
		this.players = new ArrayList<>();
		this.unackedMessages = new HashMap<>();
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
				.match(NotifyNewPlayerMsg.class, msg -> getContext()
						.actorSelection("akka://DistributedSystem@" + msg.getHost() + ":" + msg.getPort() + "/user/*")
						.tell(new NewPlayerMsg(), getSelf()))
				.match(NewPlayerMsg.class, msg -> {
					System.out.println("received new player: " + getSender());
					this.addToPlayers(getSender());
				})
				.match(PlayerAcceptedMsg.class, msg -> {
					this.players = msg.getCurrentPlayers();
					board.setCurrentPositions(msg.getCurrentPositions());
					System.out.println("PLAYER ACCEPTED by the distributed system");
					System.out.println("Players: " + msg.getCurrentPlayers());
					System.out.println("Board: " + msg.getCurrentPositions());
					this.tellNextPlayer(new UpdatePlayersMsg(this.players));
					getSender().tell(new AckMsg(msg), getSelf());
				})
				.match(UpdatePlayersMsg.class, msg -> {
					if (!players.equals(msg.getPlayers())){
						System.out.println("Update players from " + getSender() + " - New players: " + msg.getPlayers());
						this.players = msg.getPlayers();
						this.tellNextPlayer(new UpdatePlayersMsg(msg.getPlayers()));
					} else {
						System.out.println("Update players from " + getSender() + " - Update stopped.");
					}
					getSender().tell(new AckMsg(msg), getSelf());
				})
				.match(UpdateNextMsg.class, msg -> this.tellNextPlayer(new UpdateTilesMsg(this.board.getCurrentPositions())))
				.match(UpdateTilesMsg.class, msg -> {
					if (!board.getCurrentPositions().equals(msg.getCurrentPositions())){
						System.out.println("Update tiles from " + getSender() + " - New tiles: " + msg.getCurrentPositions());
						board.setCurrentPositions(msg.getCurrentPositions());
						this.tellNextPlayer(new UpdateTilesMsg(msg.getCurrentPositions()));
					} else {
						System.out.println("Update tiles from " + getSender() + " - Update stopped.");
					}
					getSender().tell(new AckMsg(msg), getSelf());
				})
				.match(SendReplayMsg.class, msg -> {
					if (this.unackedMessages.containsKey(msg.getReplayMsg().getUniqueID())){
						System.out.println("REPLAY message " + msg.getReplayMsg().getClass() + msg.getReplayMsg().getUniqueID() +" for " +  getNextPlayer());
						this.tellNextPlayer(msg.getReplayMsg());
					}
				})
				.match(AckMsg.class, msg -> {
					this.unackedMessages.remove(msg.getPrevMsg().getUniqueID());
					getTimers().cancel(msg.getPrevMsg().getUniqueID());
				})
				.match(PlayerExitMsg.class, msg -> {
					System.out.println(getSender() + " EXITED!");
					this.removeFromPlayers(msg.getExitedActor());
				})
				.match(PlayerFailMsg.class, msg -> {
					System.out.println("CAN'T SEND MESSAGES TO " + getSender() + "!");
					this.removeFromPlayers(msg.getFailedActor());
					if (msg.getMsgToSend().getClass() != UpdatePlayersMsg.class){
						this.tellNextPlayer(msg.getMsgToSend());
					}
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

	private void addToPlayers(final ActorRef player){
		final int playerIndex = (this.players.indexOf(getSelf()) + 1) % players.size();
		if (playerIndex == 0){
			this.players.add(player);
		} else {
			this.players.add(playerIndex, player);
		}
		this.tellNextPlayer(new PlayerAcceptedMsg(this.board.getCurrentPositions(), this.players));
	}

	private void removeFromPlayers(final ActorRef player){
		this.players.remove(player);
		this.tellNextPlayer(new UpdatePlayersMsg(this.players));
	}

	private void tellNextPlayer(final UniqueIdMsg message){
		ActorRef nextPlayer = getNextPlayer();
		if (nextPlayer != getSelf()){
			nextPlayer.tell(message, getSelf());
			final String msgId = message.getUniqueID();
			if (unackedMessages.containsKey(msgId)){
				if (this.unackedMessages.get(msgId) == MAX_ATTEMPTS){
					getTimers().cancel(msgId);
					getSelf().tell(new PlayerFailMsg(nextPlayer, message), getSelf());
					this.unackedMessages.remove(msgId);
				} else {
					this.unackedMessages.put(msgId, this.unackedMessages.get(msgId) + 1);
				}
			} else {
				this.unackedMessages.put(msgId, 1);
				getTimers().startTimerWithFixedDelay(msgId, new SendReplayMsg(message), Duration.ofMillis(500));
			}
		}
	}

	private ActorRef getNextPlayer(){
		return players.get((this.players.indexOf(getSelf()) + 1) % players.size());
	}

	@Override
	public void postStop() throws Exception {
		System.out.println("CLOSED.");
		for (ActorRef p: players){
			p.tell(new PlayerExitMsg(getSelf()), getSelf());
		}
		super.postStop();
	}
}