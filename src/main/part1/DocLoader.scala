import Coordinator.{AnalyzerDone, LoaderDone, StartAnalyzer}
import Main.{PAGES_EACH_ANALYZER, REGEX}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import java.io.{File, IOException}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object DocLoader {
  final case class Load() extends Msg
  final case class GetWords(doc: PDDocument, p: Int, totP: Int, stripper: PDFTextStripper, docId: Int) extends Msg
  final case class CloseDoc(doc:PDDocument) extends Msg

  def apply(f: File, id: Int, coordRef: ActorRef[Msg]) : Behavior[Msg] = Behaviors.receive {
    (context, message) =>
      message match {
        case Load() =>
          implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("blocking-dispatcher"))
          Future {
            print(s"D: Loading ${f.getName}\n")
            val document = PDDocument.load(f)
            val ap = document.getCurrentAccessPermission
            if (!ap.canExtractContent) throw new IOException("You do not have permission to extract text")
            print(s"D: DONE Loading ${f.getName}\n")
            document
          }.onComplete {
            case Success(doc) => context.self ! GetWords(doc, 0, doc.getNumberOfPages, new PDFTextStripper(), id)
            case Failure(exception) => print(s"Exception in Loading ($exception) in ${context.self.path.name}")
          }
          Behaviors.same
        case GetWords(doc, p, totP, stripper, docId) =>
          implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("blocking-dispatcher"))
          Future {
            stripper.setStartPage(p)
            stripper.setEndPage(Math.min(p + PAGES_EACH_ANALYZER - 1 , totP))
            val words: Array[String] = stripper.getText(doc).split(REGEX)
            (p < totP, words)
          }.onComplete {
            case Success((true, w)) =>
              context.self ! GetWords(doc, p + PAGES_EACH_ANALYZER, totP, stripper, docId)
              if (w.length > 0) {
                coordRef ! StartAnalyzer(p, docId, w)
              } else {
                coordRef ! AnalyzerDone()
              }
            case Success((false, _)) =>
              context.self ! DocLoader.CloseDoc(doc)
              coordRef ! LoaderDone();
            case Failure(exception) => print(s"Exception in Stripper $docId at page $p, totPages $totP ($exception). \n")
          }
          Behaviors.same
        case CloseDoc(doc) =>
          doc.close()
          context.log.info(s"${f.getName} closed")
          Behaviors.stopped
        case _ => Behaviors.stopped
      }
  }
}