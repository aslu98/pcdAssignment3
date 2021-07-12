package lab09.actors.hello_typed;

public final class HelloMsg implements BaseMsg {
	private final String content;

	public HelloMsg(String content){
		this.content = content;
	}
	
	public String getContent(){
		return content;
	}
}
