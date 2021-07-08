import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}

import java.io.{BufferedReader, File, FileReader}
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object IgnoreFileLoader {

  sealed trait FileLoaderMsg extends Msg

  final case class Load() extends FileLoaderMsg

  def apply(f: File, coordRef: ActorRef[Msg]): Behavior[Msg] = Behaviors.receive {
    (context, message) =>
      message match {
        case Load() =>
          implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("blocking-dispatcher"))
          Future {
            val fr = new FileReader(f)
            val br = new BufferedReader(fr)
            var wordsToDiscard: immutable.List[String] = List()
            br.lines.forEach((w: String) => {
              wordsToDiscard = w :: wordsToDiscard
            })
            fr.close()
            wordsToDiscard
          }.onComplete {
            case Success(wordsToDiscard) => coordRef ! Coordinator.WordsToDiscard(wordsToDiscard)
            case Failure(exception) => print(s"Exception in Loading ignore file ($exception)")
          }
          Behaviors.same
        case _ => Behaviors.stopped
      }
  }
}
