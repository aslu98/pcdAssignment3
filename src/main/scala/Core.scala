import Coordinator.CoordMsg
import DocLoader.DocLoadMsg
import FoldersExplorer.FolderExplorerMsg
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import java.io.{BufferedReader, File, FileReader, IOException}
import scala.collection.immutable
import scala.collection.immutable.HashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Main extends App {
  val AbsolutePath = new File("").getAbsolutePath + "/src/main/resources/"
  val DirectoryPath = AbsolutePath + "PDF"
  val IgnoreFilePath = AbsolutePath + "ignored/empty.txt"
  val N = 10
  val REGEX = "[\\x{201D}\\x{201C}\\s'\", ?.@;:!-]+"
  val system: ActorSystem[Coordinator.CoordMsg] = ActorSystem(Coordinator(N, DirectoryPath, IgnoreFilePath), name = "hello-world")
}

object Coordinator {

  sealed trait CoordMsg

  final case class WordsToDiscard(wordsToDiscard: List[String]) extends CoordMsg

  final case class ExplorerDone(explorer: ActorRef[FolderExplorerMsg], totDocs: Int) extends CoordMsg

  final case class StartLoader(f: File, id: Int) extends CoordMsg

  final case class LoaderDone() extends CoordMsg

  final case class StartAnalyser(doc: PDDocument, p: Int, totP: Int, stripper: PDFTextStripper, docId: Int, docLoader: ActorRef[DocLoadMsg]) extends CoordMsg

  final case class MapUpdate(map: Map[String, Int]) extends CoordMsg

  final case class AnalyzerDone() extends CoordMsg

  final case class Stop() extends CoordMsg

  final case class Done() extends CoordMsg

  def apply(n: Int, dirpath: String, filepath: String): Behavior[CoordMsg] = {
    Behaviors.withStash(100) { buffer =>
      Behaviors.setup[CoordMsg] { context =>
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
          beforeAnalysing(context, buffer, n, explorerDone = false, 0, 0, 0, 0)
        } else {
          Behaviors.stopped
        }
      }
    }
  }

  private def merge[K](m1: Map[K, Int], m2: Map[K, Int]): Map[K, Int] =
    ((m1.keySet ++ m2.keySet) map { (i: K) => i -> (m1.getOrElse(i, 0) + m2.getOrElse(i, 0)) }).toMap

  private def startLoader(context: ActorContext[CoordMsg], f: File, id: Int): Unit = {
    val loaderRef = context.spawn(DocLoader(f, id, context.self), "doc-loader-" + id)
    loaderRef ! DocLoader.Load()
  }

  private def loaderDone(context: ActorContext[CoordMsg], explorerDone: Boolean, totDocs: Int, loadersDone: Int): Unit = {
      if(explorerDone && totDocs == loadersDone) {
        context.log.info("ALL LOADERS DONE")
        context.self ! AnalyzerDone()
      } else {
        context.log.info(s"LOAD tot: $totDocs, now: $loadersDone")
      }
}

  private def beforeAnalysing(context: ActorContext[CoordMsg], buffer: StashBuffer[CoordMsg],
                    N: Int, explorerDone: Boolean, totDocs: Int, loadersDone: Int,
                    totAnalyzers: Int, analyzersDone: Int): Behavior[CoordMsg] = {
    Behaviors.receiveMessage {
      case WordsToDiscard(wordsToDiscard) =>
        context.log.info("words to discard loaded")
        buffer.unstashAll(analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone, Map.empty[String, Int]))
      case ExplorerDone(explorer: ActorRef[FolderExplorerMsg], ndocs: Int) =>
        context.log.info(s"explorer done $ndocs")
        explorer ! FoldersExplorer.Done()
        beforeAnalysing(context, buffer, N, explorerDone = true, ndocs, loadersDone, totAnalyzers, analyzersDone)
      case StartLoader(f: File, id: Int) =>
        startLoader(context, f, id)
        Behaviors.same
      case LoaderDone() =>
        loaderDone(context, explorerDone, totDocs, loadersDone + 1)
        beforeAnalysing(context, buffer, N, explorerDone, totDocs, loadersDone + 1, totAnalyzers, analyzersDone + 1)
      case Stop() => Behaviors.stopped
      case Done() => Behaviors.stopped
      case other =>
        buffer.stash(other)
        Behaviors.same
    }
  }

  private def analysingBehaviour(context: ActorContext[CoordMsg], buffer: StashBuffer[CoordMsg],
                                 N: Int, wordsToDiscard: List[String], explorerDone: Boolean, totDocs: Int, loadersDone: Int,
                                 totAnalyzers: Int, analyzersDone: Int, wordFreqMap: Map[String, Int]):Behavior[CoordMsg] =
    Behaviors.receiveMessage {
      case ExplorerDone(explorer: ActorRef[FolderExplorerMsg], ndocs: Int) =>
        context.log.info(s"explorer done $ndocs")
        explorer ! FoldersExplorer.Done()
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone = true, ndocs, loadersDone, totAnalyzers, analyzersDone, wordFreqMap)
      case StartLoader(f: File, id: Int) =>
        startLoader(context, f, id)
        Behaviors.same
      case LoaderDone() =>
        loaderDone(context, explorerDone, totDocs, loadersDone + 1)
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone + 1, totAnalyzers, analyzersDone + 1, wordFreqMap)
      case StartAnalyser(doc, p, totP, stripper, docId, docLoaderRef) =>
        val selfRef = context.self
        val analyzerRef = context.spawn(TextAnalyzer(HashMap[String, Int](), context.self), "text-analyzer-" + docId + "-p" + p)
        implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))
        Future {
          stripper.setStartPage(p)
          stripper.setEndPage(p)
          val words: Array[String] = stripper.getText(doc).split(Main.REGEX).filter(w => !wordsToDiscard.contains(w))
          (p < totP, words)
        }.onComplete {
          case Success((true, w)) =>
            selfRef ! Coordinator.StartAnalyser(doc, p + 1, totP, stripper, docId, docLoaderRef)
            if (w.length > 0) {
              analyzerRef ! TextAnalyzer.Analyse(w, 0)
            } else {
              context.self ! AnalyzerDone()
            }
          case Success((false, _)) =>
            docLoaderRef ! DocLoader.CloseDoc(doc)
            context.self ! LoaderDone();
          case Failure(exception) => print(s"Exception in Stripper $docId at page $p, totPages $totP ($exception), dad ${selfRef.path}. \n")
        }
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers + 1, analyzersDone, wordFreqMap)
      case MapUpdate(map) =>
        val updatedMap = merge(map, wordFreqMap)
        val sortedMap = updatedMap.toList.sortBy(_._2).reverse.slice(0, N)
        /*send update to view*/
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone, updatedMap)
      case AnalyzerDone() =>
        if (explorerDone && totDocs == loadersDone && totAnalyzers == analyzersDone) {
          context.log.info("ALL ANALYZERS DONE")
          val sortedMap = wordFreqMap.toList.sortBy(_._2).reverse.slice(0, N)
          context.log.info(sortedMap.toString())
          //send done msg to view
        }
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone + 1, wordFreqMap)
      case _ => Behaviors.stopped
    }
}

object IgnoreFileLoader{
  sealed trait FileLoaderMsg
  final case class Load() extends FileLoaderMsg
  final case class Stop() extends FileLoaderMsg

  def apply(f: File, coordRef: ActorRef[CoordMsg]) : Behavior[FileLoaderMsg] = Behaviors.receive {
    (context, message) => message match {
      case Load() =>
        implicit val executionContext: ExecutionContext =
          context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))
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

object FoldersExplorer{
  sealed trait FolderExplorerMsg
  final case class Explore(dir: List[File], coordinator: ActorRef[CoordMsg]) extends FolderExplorerMsg
  final case class Stop() extends FolderExplorerMsg
  final case class Done() extends FolderExplorerMsg

  def apply(present: Int, done: Int, ndocs: Int) : Behavior[FolderExplorerMsg] = Behaviors.receive {
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
        } else {
          if (newpresent == newdone) {
            coordRef ! Coordinator.ExplorerDone(context.self, newndocs)
          }
        }
        FoldersExplorer(newpresent, newdone, newndocs)
      case Done() => FoldersExplorer()
      case _ => Behaviors.stopped
    }
  }

  def apply(): Behavior[FolderExplorerMsg] = Behaviors.receive {
    (_, message) =>
      message match {
        case _ => Behaviors.stopped
      }
  }
}

object DocLoader{
  sealed trait DocLoadMsg
  final case class Load() extends DocLoadMsg
  final case class CloseDoc(doc:PDDocument) extends DocLoadMsg
  final case class Stop() extends DocLoadMsg

  def apply(f: File, id: Int, coordRef: ActorRef[CoordMsg]) : Behavior[DocLoadMsg] = Behaviors.receive {
    (context, message) =>
      message match {
        case Load() =>
          val selfRef = context.self
          implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))
          Future {
            print(s"D: Loading ${f.getName}\n")
            val document = PDDocument.load(f)
            val ap = document.getCurrentAccessPermission
            if (!ap.canExtractContent) throw new IOException("You do not have permission to extract text")
            print(s"D: DONE Loading ${f.getName}\n")
            document
          }.onComplete {
            case Success(doc) => coordRef ! Coordinator.StartAnalyser(doc, 0, doc.getNumberOfPages, new PDFTextStripper(), id, selfRef)
            case Failure(exception) => print(s"Exception in Loading ($exception) in ${context.self.path.name}")
          }
          Behaviors.same
        case CloseDoc(doc) =>
          context.log.info(s"${f.getName} closed")
          doc.close()
          DocLoader()
        case _ => Behaviors.stopped
      }
  }

  def apply(): Behavior [DocLoadMsg] = Behaviors.receive {
    (_, message) =>
      message match {
        case _ => Behaviors.stopped
      }
  }
}

object TextAnalyzer {
  sealed trait AnalyseMsg
  final case class Analyse(words: Array[String], pos:Int) extends AnalyseMsg
  final case class Stop() extends AnalyseMsg
  final case class Done() extends AnalyseMsg

  def apply(map: Map[String, Int], coordRef: ActorRef[CoordMsg]): Behavior[AnalyseMsg] =  Behaviors.receive {
    (context, message) => message match {
      case Analyse(words, pos) =>
        val w = words(pos).trim.toLowerCase
        val updatedMap: Map[String, Int] = {
          if (map.contains(w)) {
            map + (w -> (map(w) + 1))
          } else {
            map + (w -> 1)
          }
        }
        if (pos < words.length - 1){
          context.self ! Analyse(words, pos+1)
        } else {
          coordRef ! Coordinator.MapUpdate(updatedMap)
          if (map.nonEmpty) coordRef ! Coordinator.AnalyzerDone()
        }
        TextAnalyzer(updatedMap, coordRef)
      case _ => Behaviors.stopped
    }
  }
}