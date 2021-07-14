package part2.messages.update;

import part2.messages.UniqueIdMsg;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

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
