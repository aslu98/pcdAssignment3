import Coordinator.CoordMsg
import DocLoader.DocLoadMsg
import FoldersExplorer.FolderExplorerMsg
import ViewRender.{Init, ViewMsg}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector}
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import java.awt.{BorderLayout, Dimension, LayoutManager}
import java.io.{BufferedReader, File, FileReader, IOException}
import javax.swing._
import scala.collection.immutable
import scala.collection.immutable.HashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Main extends App {
  val system: ActorSystem[ViewMsg] = ActorSystem(ViewRender(), name = "hello-world")
  system ! Init()
}

class ViewFrame(viewRef: ActorRef[ViewMsg]) extends JFrame(":: Words Freq - TASKS ::") {
  private val AbsolutePath: String = new File("").getAbsolutePath + "/src/main/resources/"
  private val DirectoryPath: String = AbsolutePath + "PDF"
  private val IgnoreFilePath: String = AbsolutePath + "ignored/empty.txt"
  private val N = 10
  private val startButton: JButton = new JButton("start")
  private val stopButton: JButton = new JButton("stop")
  private val chooseDir: JButton = new JButton("select dir")
  private val chooseFile: JButton = new JButton("select ignore file")
  private val nMostFreqWords: JTextField = new JTextField(N)
  private val actualState: JTextField = new JTextField("ready.", 40)
  private val selectedDir: JLabel = new JLabel(AbsolutePath + DirectoryPath)
  private val selectedFile: JLabel = new JLabel(AbsolutePath + IgnoreFilePath)
  private val controlPanel11: JPanel = new JPanel()
  private val controlPanel12: JPanel = new JPanel()
  private val controlPanel2: JPanel = new JPanel()
  private val controlPanel: JPanel = new JPanel()
  private val wordsPanel: JPanel = new JPanel()
  private val infoPanel: JPanel = new JPanel()
  val cp: JPanel = new JPanel()
  private val myLayout: LayoutManager = new BorderLayout()
  private val scrollPane: JScrollPane = new JScrollPane(wordsPanel)
  private val wordsFreq: JTextArea = new JTextArea(15, 40)

  def apply(viewRef: ActorRef[ViewMsg]): ViewFrame = {
    selectedDir.setSize(400, 14)
    selectedFile.setSize(400, 14)
    controlPanel11.add(chooseDir)
    controlPanel11.add(selectedDir)
    controlPanel12.add(chooseFile)
    controlPanel12.add(selectedFile)
    controlPanel2.add(new JLabel("Num words"))
    controlPanel2.add(nMostFreqWords)
    controlPanel2.add(Box.createRigidArea(new Dimension(40, 0)))
    controlPanel2.add(startButton)
    controlPanel2.add(stopButton)
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS))
    controlPanel.add(controlPanel11)
    controlPanel.add(controlPanel12)
    controlPanel.add(controlPanel2)
    wordsPanel.add(wordsFreq)
    actualState.setSize(700, 14)
    infoPanel.add(actualState)
    cp.setLayout(myLayout)
    cp.add(BorderLayout.NORTH, controlPanel)
    cp.add(BorderLayout.CENTER, scrollPane)
    cp.add(BorderLayout.SOUTH, infoPanel)
    readyToStartButtons()

    chooseDir.addActionListener(_ =>{
      val startDirectoryChooser = new JFileChooser
      startDirectoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
      if (startDirectoryChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        val dir = startDirectoryChooser.getSelectedFile
        selectedDir.setText(dir.getAbsolutePath)
      }
    })
    chooseFile.addActionListener(_ => {
      val wordsToDiscardFileChooser = new JFileChooser
      if (wordsToDiscardFileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        val wordsToDiscardFile = wordsToDiscardFileChooser.getSelectedFile
        selectedFile.setText(wordsToDiscardFile.getAbsolutePath)
      }
    })
    startButton.addActionListener(_ => {
      val dir = new File(selectedDir.getText)
      val configFile = new File(selectedFile.getText)
      val numMostFreqWords = nMostFreqWords.getText.toInt
      viewRef ! ViewRender.Start(numMostFreqWords, dir.getAbsolutePath, configFile.getAbsolutePath)
      actualState.setText("Processing...")
      startButton.setEnabled(false)
      stopButton.setEnabled(true)
      chooseDir.setEnabled(false)
      chooseFile.setEnabled(false)
    })
    stopButton.addActionListener(_ => {
      viewRef ! ViewRender.Stop()
      actualState.setText("Stopped.")
      readyToStartButtons()
    })
    this
  }

  def update(freqs: List[(String, Int)]): Unit = {
    SwingUtilities.invokeLater(() => {
        wordsFreq.setText("")
        for (entry <- freqs) {
          wordsFreq.append(entry._1 + " " + entry._2 + "\n")
        }
    })
  }

  def done(): Unit = {
    SwingUtilities.invokeLater(() => {
        readyToStartButtons()
      actualState.setText("Done.")
    })
  }

  private def readyToStartButtons(): Unit = {
    startButton.setEnabled(true)
    stopButton.setEnabled(false)
    chooseDir.setEnabled(true)
    chooseFile.setEnabled(true)
  }
}

object ViewRender{
  sealed trait ViewMsg
  final case class Start(n: Int, dirpath: String, filepath: String) extends ViewMsg
  final case class Update(map: List[(String, Int)]) extends ViewMsg
  final case class Stop() extends ViewMsg
  final case class Done() extends ViewMsg
  final case class Init() extends ViewMsg

  def apply(): Behavior[ViewMsg] = {
    Behaviors.receive {
      (context, message) => message match {
        case Init() =>
          context.log.info("Init chiamato")
          val frame = new ViewFrame(context.self)
          SwingUtilities.invokeLater(() => {
            frame.setSize(1000, 400)
            frame.setContentPane(frame.cp)
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
            frame.setVisible(true)
          })
          initialized(frame)
      }
    }
  }

  def initialized(frame: ViewFrame): Behavior[ViewMsg] = {
    Behaviors.receive {
      (context, message) => message match {
        case Start(n, dirpath, filepath) =>
          val coordRef = context.spawn(Coordinator(n, dirpath, filepath, context.self), "coordinator")
          started(frame, coordRef)
      }
    }
  }

  def started(frame: ViewFrame, coordRef: ActorRef[CoordMsg]): Behavior[ViewMsg] = {
    Behaviors.receive {
      (context, message) => message match {
        case Start(n, dirpath, filepath) =>
          val coordRef = context.spawn(Coordinator(n, dirpath, filepath, context.self), "coordinator")
          started(frame, coordRef)
        case Update(map) =>
          frame.update(map)
          Behaviors.same
        case Done() =>
          frame.done()
          Behaviors.same
        case Stop() =>
          coordRef ! Coordinator.Stop()
          Behaviors.stopped
      }
    }
  }
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

  def apply(n: Int, dirpath: String, filepath: String, viewRef: ActorRef[ViewMsg]): Behavior[CoordMsg] = {
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
          beforeAnalysing(context, buffer, n, explorerDone = false, 0, 0, 0, 0, viewRef)
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
                    totAnalyzers: Int, analyzersDone: Int, viewRef: ActorRef[ViewMsg]): Behavior[CoordMsg] = {
    Behaviors.receiveMessage {
      case WordsToDiscard(wordsToDiscard) =>
        context.log.info("words to discard loaded")
        buffer.unstashAll(analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone, Map.empty[String, Int], viewRef))
      case ExplorerDone(explorer: ActorRef[FolderExplorerMsg], ndocs: Int) =>
        context.log.info(s"explorer done $ndocs")
        explorer ! FoldersExplorer.Done()
        beforeAnalysing(context, buffer, N, explorerDone = true, ndocs, loadersDone, totAnalyzers, analyzersDone, viewRef)
      case StartLoader(f: File, id: Int) =>
        startLoader(context, f, id)
        Behaviors.same
      case LoaderDone() =>
        loaderDone(context, explorerDone, totDocs, loadersDone + 1)
        beforeAnalysing(context, buffer, N, explorerDone, totDocs, loadersDone + 1, totAnalyzers, analyzersDone + 1, viewRef)
      case Stop() => Behaviors.stopped
      case Done() => Behaviors.stopped
      case other =>
        buffer.stash(other)
        Behaviors.same
    }
  }

  private def analysingBehaviour(context: ActorContext[CoordMsg], buffer: StashBuffer[CoordMsg],
                                 N: Int, wordsToDiscard: List[String], explorerDone: Boolean, totDocs: Int, loadersDone: Int,
                                 totAnalyzers: Int, analyzersDone: Int, wordFreqMap: Map[String, Int], viewRef: ActorRef[ViewMsg]):Behavior[CoordMsg] =
    Behaviors.receiveMessage {
      case ExplorerDone(explorer: ActorRef[FolderExplorerMsg], ndocs: Int) =>
        context.log.info(s"explorer done $ndocs")
        explorer ! FoldersExplorer.Done()
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone = true, ndocs, loadersDone, totAnalyzers, analyzersDone, wordFreqMap, viewRef)
      case StartLoader(f: File, id: Int) =>
        startLoader(context, f, id)
        Behaviors.same
      case LoaderDone() =>
        loaderDone(context, explorerDone, totDocs, loadersDone + 1)
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone + 1, totAnalyzers, analyzersDone + 1, wordFreqMap, viewRef)
      case StartAnalyser(doc, p, totP, stripper, docId, docLoaderRef) =>
        val REGEX = "[\\x{201D}\\x{201C}\\s'\", ?.@;:!-]+"
        val selfRef = context.self
        val analyzerRef = context.spawn(TextAnalyzer(HashMap[String, Int](), context.self), "text-analyzer-" + docId + "-p" + p)
        implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))
        Future {
          stripper.setStartPage(p)
          stripper.setEndPage(p)
          val words: Array[String] = stripper.getText(doc).split(REGEX).filter(w => !wordsToDiscard.contains(w))
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
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers + 1, analyzersDone, wordFreqMap, viewRef)
      case MapUpdate(map) =>
        var updatedMap: Map[String, Int] = Map.empty
        if (map.nonEmpty) {
          updatedMap = merge(map, wordFreqMap)
          val sortedMap = updatedMap.toList.sortBy(_._2).reverse.slice(0, N)
          viewRef ! ViewRender.Update(sortedMap)
        } else {
          context.log.info("empty map")
          updatedMap = wordFreqMap
        }
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone, updatedMap, viewRef)
      case AnalyzerDone() =>
        if (explorerDone && totDocs == loadersDone && totAnalyzers == analyzersDone) {
          context.log.info("ALL ANALYZERS DONE")
          val sortedMap = wordFreqMap.toList.sortBy(_._2).reverse.slice(0, N)
          context.log.info(sortedMap.toString())
          viewRef ! ViewRender.Done()
        }
        analysingBehaviour(context, buffer, N, wordsToDiscard, explorerDone, totDocs, loadersDone, totAnalyzers, analyzersDone + 1, wordFreqMap, viewRef)
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
          coordRef ! Coordinator.AnalyzerDone()
        }
        TextAnalyzer(updatedMap, coordRef)
      case _ => Behaviors.stopped
    }
  }
}