package part2.messages;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public abstract class UniqueIdMsg implements Serializable {
	private final String uniqueID;

	public UniqueIdMsg(){
		this.uniqueID = UUID.randomUUID().toString();
	}

	public String getUniqueID() {
		return uniqueID;
	}
}
