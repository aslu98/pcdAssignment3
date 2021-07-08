import ViewRender.Init
import akka.actor.typed.ActorSystem

object Main extends App {
  private val system = ActorSystem(ViewRender(), name = "hello-world")
  system ! Init()
}