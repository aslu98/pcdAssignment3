import Coordinator.{AnalyzerDone, LoaderDone, StartAnalyzer}
import Main.{PAGES_EACH_ANALYZER, REGEX}
import ViewRender.Init
import akka.actor.InvalidActorNameException
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector}
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import java.io.{BufferedReader, File, FileReader, IOException}
import scala.collection.immutable
import scala.collection.immutable.HashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait Msg
final case class Stop() extends Msg
final case class Done() extends Msg

object Main extends App {
  val REGEX = "[\\x{201D}\\x{201C}\\s'\", ?.@;:!-]+"
  val PAGES_EACH_ANALYZER = 4

  val system = ActorSystem(ViewRender(), name = "hello-world")
  system ! Init()
}

object Coordinator {
  sealed trait CoordMsg extends Msg
  final case class WordsToDiscard(wordsToDiscard: List[String]) extends CoordMsg
  final case class ExplorerDone(explorer: ActorRef[Msg], totDocs: Int) extends CoordMsg
  final case class StartLoader(f: File, id: Int) extends CoordMsg
  final case class LoaderDone() extends CoordMsg
  final case class StartAnalyzer(p: Int, docId: Int, words: Array[String]) extends CoordMsg
  final case class MapUpdate(map: Map[String, Int], analyzerWords: Int) extends CoordMsg
  final case class AnalyzerDone() extends CoordMsg
  final case class Restart(n: Int, dirpath: String, filepath: String, viewRef: ActorRef[Msg]) extends CoordMsg

  private def setInputs(context: ActorContext[Msg], buffer: StashBuffer[Msg],
                        n: Int, dirpath: String, filepath: String, viewRef: ActorRef[Msg]): Behavior[Msg] ={
    context.log.info("N: {}", n)
    context.log.info("dirpath: {}", dirpath)
    context.log.info("filepath: {}", filepath)

    val ignore: File = new File(filepath)
    val ignoreRef = context.spawn(IgnoreFileLoader(ignore, context.self), "ignore-loader")
    ignoreRef ! IgnoreFileLoader.Load()

    val dir: File = new File(dirpath)
    if (dir.isDirectory) {
      val explorerRef = context.spawn(FoldersExplorer(dir.listFiles().length, 0, 0), "folder-explorer")
      explorerRef ! FoldersExplorer.Explore(dir.listFiles().toList, context.self)
      beforeAnalyzing(context, buffer, n, explorerDone = false, 0, 0, 0, 0, viewRef)
    } else {
      stopped(context, buffer)
    }
  }

  private def merge[K](m1: Map[K, Int], m2: Map[K, Int]): Map[K, Int] =
    ((m1.keySet ++ m2.keySet) map { (i: K) => i -> (m1.getOrElse(i, 0) + m2.getOrElse(i, 0)) }).toMap

  private def startLoader(context: ActorContext[Msg], f: File, id: Int): Unit = {
    val loaderRef = context.spawn(DocLoader(f, id, context.self), "doc-loader-" + id)
    loaderRef ! DocLoader.Load()
  }

  private def loaderDone(context: ActorContext[Msg], explorerDone: Boolean, totDocs: Int, loadersDone: Int): Unit = {
    if(explorerDone && totDocs == loadersDone) {
      context.log.info("ALL LOADERS DONE")
      context.self ! AnalyzerDone()
    } else {
      context.log.info(s"LOAD tot: $totDocs, now: $loadersDone")
    }
  }

  def apply(n: Int, dirpath: String, filepath: String, viewRef: ActorRef[Msg]): Behavior[Msg] = {
    Behaviors.withStash(100) { buffer =>
      Behaviors.setup[Msg] { context =>
        setInputs(context, buffer, n, dirpath, filepath, viewRef)
      }
    }
  }

  private def beforeAnalyzing(context: ActorContext[Msg], buffer: StashBuffer[Msg],
                    N: Int, explorerDone: Boolean, totDocs: Int, loadersDone: Int,
                    totAnalyzers: Int, analyzersDone: Int, viewRef: ActorRef[Msg]): Behavior[Msg] = {
    Behaviors.receiveMessage {
      case WordsToDiscard(wordsToDiscard) =>
        context.log.info("words to discard loaded")
        buffer.unstashAll(analyzingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone, Map.empty[String, Int], 0, viewRef))
      case ExplorerDone(explorer: ActorRef[Msg], ndocs: Int) =>
        context.log.info(s"explorer done $ndocs")
        explorer ! Done()
        beforeAnalyzing(context, buffer, N, explorerDone = true, ndocs, loadersDone, totAnalyzers, analyzersDone, viewRef)
      case StartLoader(f: File, id: Int) =>
        startLoader(context, f, id)
        Behaviors.same
      case LoaderDone() =>
        loaderDone(context, explorerDone, totDocs, loadersDone + 1)
        beforeAnalyzing(context, buffer, N, explorerDone, totDocs, loadersDone + 1, totAnalyzers, analyzersDone, viewRef)
      case Stop() => stopped(context, buffer)
      case Done() => stopped(context, buffer)
      case other =>
        buffer.stash(other)
        Behaviors.same
    }
  }

  private def analyzingBehaviour(context: ActorContext[Msg], buffer: StashBuffer[Msg],
                                 N: Int, wordsToDiscard: List[String], explorerDone: Boolean, totDocs: Int, loadersDone: Int,
                                 totAnalyzers: Int, analyzersDone: Int, wordFreqMap: Map[String, Int], totWords: Int, viewRef: ActorRef[Msg]):Behavior[Msg] =
    Behaviors.receiveMessage {
      case ExplorerDone(explorer: ActorRef[Msg], ndocs: Int) =>
        context.log.info(s"explorer done $ndocs")
        explorer ! Done()
        analyzingBehaviour(context, buffer, N, wordsToDiscard, explorerDone = true, ndocs, loadersDone, totAnalyzers, analyzersDone, wordFreqMap, totWords, viewRef)
      case StartLoader(f: File, id: Int) =>
        startLoader(context, f, id)
        Behaviors.same
      case LoaderDone() =>
        loaderDone(context, explorerDone, totDocs, loadersDone + 1)
        analyzingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone + 1, totAnalyzers, analyzersDone, wordFreqMap, totWords, viewRef)
      case StartAnalyzer(p, docId, words) =>
        val name = "text-analyzer-" + docId + "-p" + p
        val analyzerRef =
          try {
            context.spawn(TextAnalyzer(HashMap[String, Int](), context.self), name)
          } catch  {
            case _: InvalidActorNameException =>
              context.children.asInstanceOf[Iterable[ActorRef[Msg]]].filter(child => child.path.name == name).foreach(child => child ! Stop())
              context.spawn(TextAnalyzer(HashMap[String, Int](), context.self), name)
          }
        analyzerRef ! TextAnalyzer.Analyze(words.filter(w => !wordsToDiscard.contains(w)), 0)
        analyzingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers + 1, analyzersDone, wordFreqMap, totWords, viewRef)
      case MapUpdate(map, analyzerWords) =>
        var updatedMap: Map[String, Int] = Map.empty
        if (map.nonEmpty) {
          updatedMap = merge(map, wordFreqMap)
          val sortedMap = updatedMap.toList.sortBy(_._2).reverse.slice(0, N)
          viewRef ! ViewRender.Update(sortedMap, loadersDone, totWords + analyzerWords)
        } else {
          context.log.info("empty map")
          updatedMap = wordFreqMap
        }
        analyzingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone, updatedMap, totWords + analyzerWords, viewRef)
      case AnalyzerDone() =>
        if (explorerDone && totDocs == loadersDone && totAnalyzers == analyzersDone) {
          context.log.info("ALL ANALYZERS DONE")
          val sortedMap = wordFreqMap.toList.sortBy(_._2).reverse.slice(0, N)
          context.log.info(sortedMap.toString())
          viewRef ! Done()
          context.self ! Done()
        }
        analyzingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone + 1, wordFreqMap, totWords, viewRef)
      case _ =>
        context.children.asInstanceOf[Iterable[ActorRef[Msg]]].foreach(child => child ! Stop())
        stopped(context, buffer)
    }


  private def stopped(context: ActorContext[Msg], buffer: StashBuffer[Msg]) : Behavior[Msg] =
    Behaviors.receiveMessage {
          case Restart(n, dirpath, filepath, viewRef) =>
            setInputs(context, buffer, n, dirpath, filepath, viewRef)
          case _ => Behaviors.same
    }
}

object IgnoreFileLoader {
  sealed trait FileLoaderMsg extends Msg
  final case class Load() extends FileLoaderMsg

  def apply(f: File, coordRef: ActorRef[Msg]) : Behavior[Msg] = Behaviors.receive {
    (context, message) => message match {
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
        }.onComplete{
          case Success(wordsToDiscard) => coordRef ! Coordinator.WordsToDiscard(wordsToDiscard)
          case Failure(exception) => print(s"Exception in Loading ignore file ($exception)")
        }
        Behaviors.same
      case _ => Behaviors.stopped
    }
  }
}

object FoldersExplorer {
  final case class Explore(dir: List[File], coordinator: ActorRef[Msg]) extends Msg

  def apply(present: Int, done: Int, ndocs: Int) : Behavior[Msg] = Behaviors.receive {
    (context, message) => message match {
      case Explore(dir, coordRef) =>
        val f: File = dir.head
        var newndocs = ndocs
        val newdone = done + 1
        var newpresent = present
        if (f.isDirectory){
          context.self ! FoldersExplorer.Explore(f.listFiles().toList, coordRef)
          newpresent = present + f.listFiles().length
        } else if (f.getName.endsWith(".pdf")){
          newndocs += 1
          coordRef ! Coordinator.StartLoader(f, newndocs)
        }
        val newdir = dir.drop(1)
        if (newdir.nonEmpty){
          context.self ! FoldersExplorer.Explore(newdir, coordRef)
        }
        else {
          if (newpresent == newdone) {
            coordRef ! Coordinator.ExplorerDone(context.self, newndocs)
          }
        }
        FoldersExplorer(newpresent, newdone, newndocs)
      case _ => Behaviors.stopped
    }
  }
}

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

object TextAnalyzer {
  final case class Analyze(words: Array[String], pos:Int) extends Msg

  def apply(map: Map[String, Int], coordRef: ActorRef[Msg]): Behavior[Msg] =  Behaviors.receive {
    (context, message) => message match {
      case Analyze(words, pos) =>
        val w = words(pos).trim.toLowerCase
        val updatedMap: Map[String, Int] = {
          if (map.contains(w)) {
            map + (w -> (map(w) + 1))
          } else {
            map + (w -> 1)
          }
        }
        if (pos < words.length - 1){
          context.self ! Analyze(words, pos+1)
        } else {
          coordRef ! Coordinator.MapUpdate(updatedMap, words.length)
          coordRef ! Coordinator.AnalyzerDone()
        }
        TextAnalyzer(updatedMap, coordRef)
      case _ => Behaviors.stopped
    }
  }
}