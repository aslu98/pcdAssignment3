package lab09.actors_remote.pingpong;

import java.io.Serializable;

public class PingMsg implements Serializable {

	private long value;
	
	public PingMsg(long value){
		this.value = value;
	}
	
	public long getValue(){
		return value;
	}
}
