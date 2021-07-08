import akka.actor.typed.ActorSystem

object Main extends App {
  val REGEX = "[\\x{201D}\\x{201C}\\s'\", ?.@;:!-]+"
  val PAGES_EACH_ANALYZER = 4

  val system = ActorSystem(ViewRender(), name = "hello-world")
  system ! Init()
}