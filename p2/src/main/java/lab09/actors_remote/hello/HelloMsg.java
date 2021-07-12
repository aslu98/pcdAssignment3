package lab09.actors_remote.hello;

import java.io.Serializable;

public final class HelloMsg implements Serializable {
	private final String content;

	public HelloMsg(String content){
		this.content = content;
	}
	
	public String getContent(){
		return content;
	}
}
