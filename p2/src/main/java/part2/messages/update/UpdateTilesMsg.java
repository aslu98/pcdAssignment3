package part2.messages.update;

import java.io.Serializable;
import java.util.List;

public final class UpdateTilesMsg implements Serializable {
	private final List<Integer> positions;

	public UpdateTilesMsg(List<Integer> positions){
		this.positions = positions;
	}
	
	public List<Integer> getCurrentPositions(){
		return positions;
	}
}
