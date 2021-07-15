package part2.akka.messages.update;

import part2.akka.messages.UniqueIdMsg;

import java.util.List;

public final class UpdateTilesMsg extends UniqueIdMsg {
	private static final long serialVersionUID = 2L;
	private final List<Integer> positions;

	public UpdateTilesMsg(List<Integer> positions){
		this.positions = positions;
	}
	
	public List<Integer> getCurrentPositions(){
		return positions;
	}
}
