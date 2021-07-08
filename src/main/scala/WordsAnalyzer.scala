import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object WordsAnalyzer {

  final case class Analyze(words: Array[String], pos: Int) extends Msg

  def apply(map: Map[String, Int], coordRef: ActorRef[Msg]): Behavior[Msg] = Behaviors.receive {
    (context, message) =>
      message match {
        case Analyze(words, pos) =>
          val w = words(pos).trim.toLowerCase
          val updatedMap: Map[String, Int] = {
            if (map.contains(w)) {
              map + (w -> (map(w) + 1))
            } else {
              map + (w -> 1)
            }
          }
          if (pos < words.length - 1) {
            context.self ! Analyze(words, pos + 1)
          } else {
            coordRef ! Coordinator.MapUpdate(updatedMap, words.length)
            coordRef ! Coordinator.AnalyzerDone()
          }
          WordsAnalyzer(updatedMap, coordRef)
        case _ => Behaviors.stopped
      }
  }
}
