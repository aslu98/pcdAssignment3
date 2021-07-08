import ViewRender.Init
import akka.actor.InvalidActorNameException
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import java.io.File
import scala.collection.immutable.HashMap

trait Msg
final case class Stop() extends Msg
final case class Done() extends Msg

object Coordinator {
  sealed trait CoordMsg extends Msg
  final case class WordsToDiscard(wordsToDiscard: List[String]) extends CoordMsg
  final case class ExplorerDone(totDocs: Int) extends CoordMsg
  final case class StartLoader(f: File, id: Int) extends CoordMsg
  final case class LoaderDone() extends CoordMsg
  final case class StartAnalyzer(p: Int, docId: Int, words: Array[String]) extends CoordMsg
  final case class MapUpdate(map: Map[String, Int], analyzerWords: Int) extends CoordMsg
  final case class AnalyzerDone() extends CoordMsg
  final case class Restart(n: Int, dirpath: String, filepath: String, viewRef: ActorRef[Msg]) extends CoordMsg

  private def init(context: ActorContext[Msg], buffer: StashBuffer[Msg],
                        n: Int, dirpath: String, filepath: String, viewRef: ActorRef[Msg]): Behavior[Msg] ={
    context.log.info("N: {}", n)
    context.log.info("dirpath: {}", dirpath)
    context.log.info("filepath: {}", filepath)

    val dir: File = new File(dirpath)
    if (dir.isDirectory) {
      val ignore: File = new File(filepath)
      val ignoreRef = context.spawn(IgnoreFileLoader(ignore, context.self), "ignore-loader")
      ignoreRef ! IgnoreFileLoader.Load()
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
      Behaviors.setup[Msg] { context => init(context, buffer, n, dirpath, filepath, viewRef) }
    }
  }

  private def beforeAnalyzing(context: ActorContext[Msg], buffer: StashBuffer[Msg],
                    N: Int, explorerDone: Boolean, totDocs: Int, loadersDone: Int,
                    totAnalyzers: Int, analyzersDone: Int, viewRef: ActorRef[Msg]): Behavior[Msg] = {
    Behaviors.receiveMessage {
      case WordsToDiscard(wordsToDiscard) =>
        context.log.info("words to discard loaded")
        buffer.unstashAll(analyzing(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone, Map.empty[String, Int], 0, viewRef))
      case ExplorerDone(ndocs: Int) =>
        context.log.info(s"explorer done $ndocs")
        beforeAnalyzing(context, buffer, N, explorerDone = true, ndocs, loadersDone, totAnalyzers, analyzersDone, viewRef)
      case StartLoader(f: File, id: Int) =>
        startLoader(context, f, id)
        Behaviors.same
      case LoaderDone() =>
        loaderDone(context, explorerDone, totDocs, loadersDone + 1)
        beforeAnalyzing(context, buffer, N, explorerDone, totDocs, loadersDone + 1, totAnalyzers, analyzersDone, viewRef)
      case Stop() =>
        context.children.asInstanceOf[Iterable[ActorRef[Msg]]].foreach(child => child ! Stop())
        stopped(context, buffer)
      case Done() =>
        stopped(context, buffer)
      case other =>
        buffer.stash(other)
        Behaviors.same
    }
  }

  private def analyzing(context: ActorContext[Msg], buffer: StashBuffer[Msg],
                                 N: Int, wordsToDiscard: List[String], explorerDone: Boolean, totDocs: Int, loadersDone: Int,
                                 totAnalyzers: Int, analyzersDone: Int, wordFreqMap: Map[String, Int], totWords: Int, viewRef: ActorRef[Msg]):Behavior[Msg] =
    Behaviors.receiveMessage {
      case ExplorerDone(ndocs: Int) =>
        context.log.info(s"explorer done $ndocs")
        analyzing(context, buffer, N, wordsToDiscard, explorerDone = true, ndocs, loadersDone, totAnalyzers, analyzersDone, wordFreqMap, totWords, viewRef)
      case StartLoader(f: File, id: Int) =>
        startLoader(context, f, id)
        Behaviors.same
      case LoaderDone() =>
        loaderDone(context, explorerDone, totDocs, loadersDone + 1)
        analyzing(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone + 1, totAnalyzers, analyzersDone, wordFreqMap, totWords, viewRef)
      case StartAnalyzer(p, docId, words) =>
        val name = "text-analyzer-" + docId + "-p" + p
        val analyzerRef =
          try {
            context.spawn(WordsAnalyzer(HashMap[String, Int](), context.self), name)
          } catch  {
            case _: InvalidActorNameException =>
              context.children.asInstanceOf[Iterable[ActorRef[Msg]]].filter(child => child.path.name == name).foreach(child => child ! Stop())
              context.spawn(WordsAnalyzer(HashMap[String, Int](), context.self), name + "new")
          }
        analyzerRef ! WordsAnalyzer.Analyze(words.filter(w => !wordsToDiscard.contains(w)), 0)
        analyzing(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers + 1, analyzersDone, wordFreqMap, totWords, viewRef)
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
        analyzing(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone, updatedMap, totWords + analyzerWords, viewRef)
      case AnalyzerDone() =>
        if (explorerDone && totDocs == loadersDone && totAnalyzers == analyzersDone) {
          context.log.info("ALL ANALYZERS DONE")
          val sortedMap = wordFreqMap.toList.sortBy(_._2).reverse.slice(0, N)
          context.log.info(sortedMap.toString())
          viewRef ! Done()
          context.self ! Done()
        }
        analyzing(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone + 1, wordFreqMap, totWords, viewRef)
      case _ =>
        context.children.asInstanceOf[Iterable[ActorRef[Msg]]].foreach(child => child ! Stop())
        stopped(context, buffer)
    }


  private def stopped(context: ActorContext[Msg], buffer: StashBuffer[Msg]) : Behavior[Msg] =
    Behaviors.receiveMessage {
          case Restart(n, dirpath, filepath, viewRef) => init(context, buffer, n, dirpath, filepath, viewRef)
          case _ => Behaviors.same
    }
}


