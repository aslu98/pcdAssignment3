import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import java.io.File

object FoldersExplorer {

  final case class Explore(dir: List[File], coordinator: ActorRef[Msg]) extends Msg

  def apply(present: Int, done: Int, ndocs: Int): Behavior[Msg] = Behaviors.receive {
    (context, message) =>
      message match {
        case Explore(dir, coordRef) =>
          val f: File = dir.head
          var newndocs = ndocs
          val newdone = done + 1
          var newpresent = present
          if (f.isDirectory) {
            context.self ! FoldersExplorer.Explore(f.listFiles().toList, coordRef)
            newpresent = present + f.listFiles().length
          } else if (f.getName.endsWith(".pdf")) {
            newndocs += 1
            coordRef ! Coordinator.StartLoader(f, newndocs)
          }
          val newdir = dir.drop(1)
          if (newdir.nonEmpty) {
            context.self ! FoldersExplorer.Explore(newdir, coordRef)
          }
          else {
            if (newpresent == newdone) {
              coordRef ! Coordinator.ExplorerDone(newndocs)
              context.self ! Done()
            }
          }
          FoldersExplorer(newpresent, newdone, newndocs)
        case _ => Behaviors.stopped
      }
  }
}
