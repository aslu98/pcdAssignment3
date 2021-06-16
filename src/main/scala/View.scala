import Coordinator.Restart
import Main.{Done, Msg, Stop}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import java.awt.{BorderLayout, Dimension, LayoutManager}
import java.io.File
import javax.swing.{Box, BoxLayout, JButton, JFileChooser, JFrame, JLabel, JPanel, JScrollPane, JTextArea, JTextField, SwingUtilities}

class ViewFrame(viewRef: ActorRef[Msg]) extends JFrame(":: Words Freq - TASKS ::") {
  private val AbsolutePath: String = new File("").getAbsolutePath + "/src/main/resources/"
  private val DirectoryPath: String = AbsolutePath + "PDF"
  private val IgnoreFilePath: String = AbsolutePath + "ignored/empty.txt"
  private val N = 10
  private val startButton: JButton = new JButton("start")
  private val stopButton: JButton = new JButton("stop")
  private val chooseDir: JButton = new JButton("select dir")
  private val chooseFile: JButton = new JButton("select ignore file")
  private val nMostFreqWords: JTextField = new JTextField(N.toString)
  private val actualState: JTextField = new JTextField("ready.", 40)
  private val selectedDir: JLabel = new JLabel(DirectoryPath)
  private val selectedFile: JLabel = new JLabel(IgnoreFilePath)
  private val controlPanel11: JPanel = new JPanel()
  private val controlPanel12: JPanel = new JPanel()
  private val controlPanel2: JPanel = new JPanel()
  private val controlPanel: JPanel = new JPanel()
  private val wordsPanel: JPanel = new JPanel()
  private val infoPanel: JPanel = new JPanel()
  private val cp: JPanel = new JPanel()
  private val myLayout: LayoutManager = new BorderLayout()
  private val scrollPane: JScrollPane = new JScrollPane(wordsPanel)
  private val wordsFreq: JTextArea = new JTextArea(15, 40)
  private var startTime: Long = 0

  def init(): Unit ={
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
      startTime = System.currentTimeMillis
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
      viewRef ! Stop()
      actualState.setText("Stopped.")
      readyToStartButtons()
    })

    setContentPane(cp)
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    setSize(1000, 400)
    setVisible(true)
  }

  def update(freqs: List[(String, Int)], ndocs: Int, nwords:Int): Unit = {
    SwingUtilities.invokeLater(() => {
      val now: Long = System.currentTimeMillis
      wordsFreq.setText("Info: " + (now - startTime) + " millis elapsed - " + ndocs + " docs - " + nwords + " words\n")
      for (i <- freqs.indices) {
        val end = if (i%3==0)  "\n" else "\t"
        wordsFreq.append(freqs(i)._1 + " -> " + freqs(i)._2 + end)
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

object ViewRender {
  sealed trait ViewMsg extends Msg
  final case class Start(n: Int, dirpath: String, filepath: String) extends ViewMsg
  final case class Update(map: List[(String, Int)], ndocs: Int, nwords:Int) extends ViewMsg
  final case class Init() extends ViewMsg

  def apply(): Behavior[Msg] = {
    Behaviors.receive {
      (context, message) => message match {
        case Init() =>
          val frame = new ViewFrame(context.self)
          frame.init()
          initialized(frame)
      }
    }
  }

  def initialized(frame: ViewFrame): Behavior[Msg] = {
    Behaviors.receive {
      (context, message) => message match {
        case Start(n, dirpath, filepath) =>
          val coordRef = context.spawn(Coordinator(n, dirpath, filepath, context.self), "coordinator")
          started(frame, coordRef)
      }
    }
  }

  def started(frame: ViewFrame, coordRef: ActorRef[Msg]): Behavior[Msg] = {
    Behaviors.receive {
      (context, message) => message match {
        case Start(n, dirpath, filepath) =>
          coordRef ! Restart(n, dirpath, filepath, context.self)
        case Update(map, ndocs, nwords) =>
          frame.update(map, ndocs, nwords)
        case Done() =>
          frame.done()
        case Stop() =>
          coordRef ! Stop()
      }
        Behaviors.same
    }
  }
}