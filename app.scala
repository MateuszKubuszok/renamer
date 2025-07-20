package renamer

import java.io.File
import java.nio.file.Path
import scala.collection.immutable.ListMap

import tui.*
import tui.crossterm.{ Color => _, * }
import tui.widgets.*
import scala.util.Try
import scala.util.matching.Regex

final class App(files: Array[FileInfo]) {

  private var quit = false

  // Mode

  private enum Mode {
    case InputEdit
    case OutputEdit
    case MatchPreview(selected: FileInfo)
  }

  private var mode = Mode.InputEdit

  private def modeUp(): Unit = mode = mode match {
    case Mode.InputEdit  => Mode.InputEdit
    case Mode.OutputEdit => Mode.InputEdit
    case Mode.MatchPreview(selected) =>
      Results.previousMatch(selected) match {
        case Some(prev) => Mode.MatchPreview(prev)
        case None       => Mode.OutputEdit
      }
  }

  private def modeDown(): Unit = mode = mode match {
    case Mode.InputEdit => Mode.OutputEdit
    case Mode.OutputEdit =>
      Results.headOption match {
        case Some(head) => Mode.MatchPreview(head)
        case None       => Mode.OutputEdit
      }
    case current @ Mode.MatchPreview(selected) =>
      Results.nextMatch(selected) match {
        case Some(next) => Mode.MatchPreview(next)
        case None       => current
      }
  }

  // UI

  val headerStyle   = Style.DEFAULT.addModifier(Modifier.BOLD)
  val selectedStyle = Style.DEFAULT.fg(Color.Blue).addModifier(Modifier.BOLD)
  val invalidStyle  = Style.DEFAULT.fg(Color.Red)

  object FilterInput {

    private var rawPattern: String = ""
    private var valid = true

    var pattern: Regex = null

    def addChar(c: Char): Unit = {
      rawPattern += c
      update()
    }

    def removeChar(): Unit =
      if (rawPattern.nonEmpty) {
        rawPattern = rawPattern.dropRight(1)
        update()
      }

    def update(): Unit = {
      if (rawPattern.isEmpty) {
        pattern = Regex(".*")
        valid = true
      } else {
        try {
          pattern = Regex(rawPattern)
          valid = true
        } catch {
          case _: Throwable =>
            pattern = Regex(".*")
            valid = false
        }
      }
      Results.updateMatches()
    }

    private def isSelected = mode == Mode.InputEdit

    def render(parent: Frame, rect: Rect) = {
      val input = ParagraphWidget(
        text = Text.nostyle(rawPattern),
        block = Some(
          BlockWidget(
            title = Some(Spans.nostyle("Filter")),
            titleAlignment = Alignment.Left,
            borders = Borders.ALL,
            borderType = BlockWidget.BorderType.Rounded,
            borderStyle = if (isSelected) selectedStyle else Style.DEFAULT,
            style = Style.DEFAULT
          )
        ),
        style = if (valid) Style.DEFAULT else invalidStyle,
        wrap = None,
        alignment = Alignment.Left
      )

      parent.renderWidget(input, rect)
    }
  }

  object FormatOutput {

    private var rawPattern = ""
    private var valid      = true

    def addChar(c: Char): Unit = {
      rawPattern += c
      update()
    }

    def removeChar(): Unit =
      if (rawPattern.nonEmpty) {
        rawPattern = rawPattern.dropRight(1)
        update()
      }

    var converter: Converter = Converter.Noop

    def update(): Unit = {
      if (rawPattern.isEmpty) {
        converter = Converter.Noop
        valid = true
      } else {
        Converter.parse(rawPattern) match {
          case Right(converter) =>
            this.converter = converter
            valid = true
          case Left(error) =>
            this.converter = Converter.Noop
            valid = false
        }
      }
      Results.updateMatches()
    }

    def isSelected = mode == Mode.OutputEdit

    def render(parent: Frame, rect: Rect) = {
      val input = ParagraphWidget(
        text = Text.nostyle(rawPattern),
        block = Some(
          BlockWidget(
            title = Some(Spans.nostyle("Rename to")),
            titleAlignment = Alignment.Left,
            borders = Borders.ALL,
            borderType = BlockWidget.BorderType.Rounded,
            borderStyle = if (isSelected) selectedStyle else Style.DEFAULT,
            style = Style.DEFAULT
          )
        ),
        style = Style.DEFAULT,
        wrap = None,
        alignment = Alignment.Left
      )

      parent.renderWidget(input, rect)
    }
  }

  object Results {

    final private case class Match(
      input:  FileInfo,
      output: Path,
      values: ListMap[String, String]
    )

    private var matches: Array[Match] = null

    def headOption: Option[FileInfo] = matches.headOption.map(_.input)

    def previousMatch(selected: FileInfo): Option[FileInfo] = {
      val idx = matches.indexWhere(_.input == selected)
      if (idx <= 0) None
      else Some(matches(idx - 1).input)
    }

    def nextMatch(selected: FileInfo): Option[FileInfo] = {
      val idx = matches.indexWhere(_.input == selected)
      if (idx >= matches.length - 1) None
      else Some(matches(idx + 1).input)
    }

    private def isSelected = mode match {
      case Mode.MatchPreview(_) => true
      case _                    => false
    }

    private val header = TableWidget.Row(
      cells = Array(
        TableWidget.Cell(Text.from(Spans.styled("Name", headerStyle))),
        TableWidget.Cell(Text.from(Spans.styled("Renamed", headerStyle)))
      )
    )

    private var rows: Array[TableWidget.Row] = null

    def updateMatches(): Unit = {
      matches = files
        .filter(f => FilterInput.pattern.findFirstIn(f.name).isDefined)
        .map { case fi @ FileInfo(name, path, file) =>
          val output = FormatOutput.converter(fi)
          // TODO
          Match(fi, output, ListMap.empty)
        }
        .toArray
      updateRows()
    }

    def updateRows(): Unit = {
      val selectedFile = mode match {
        case Mode.MatchPreview(selected) => selected
        case _                           => null
      }

      rows = matches.map { case Match(input, output, values) =>
        TableWidget.Row(
          cells = Array(TableWidget.Cell(Text.nostyle(input.name)), TableWidget.Cell(Text.nostyle(output.toString))),
          height = 1,
          style = if (input == selectedFile) selectedStyle else Style.DEFAULT
        )
      }
    }

    private val state = TableWidget.State()

    def render(parent: Frame, rect: Rect) = {
      val filesTable = TableWidget(
        block = Some(
          BlockWidget(
            title = Some(Spans.nostyle("Files")),
            titleAlignment = Alignment.Left,
            borders = Borders.ALL,
            borderType = BlockWidget.BorderType.Rounded,
            borderStyle = if (isSelected) selectedStyle else Style.DEFAULT,
            style = Style.DEFAULT
          )
        ),
        widths = Array(Constraint.Percentage(50), Constraint.Length(30), Constraint.Min(10)),
        highlightStyle = Style(addModifier = Modifier.REVERSED),
        highlightSymbol = Some("*"),
        header = Some(header),
        rows = rows
      )

      parent.renderStatefulWidget(filesTable, rect)(state)
    }
  }

  def draw(f: Frame): Unit = {
    val rects = Layout(
      direction = Direction.Vertical,
      constraints = Array(Constraint.Length(3), Constraint.Length(3), Constraint.Min(10)),
      margin = Margin(1)
    ).split(f.size)
    FilterInput.render(f, rects(0))
    FormatOutput.render(f, rects(1))
    Results.render(f, rects(2))
  }

  def handle(jni: CrosstermJni): Unit = jni.read() match {
    case key: Event.Key =>
      key.keyEvent.code match {
        case _: KeyCode.Esc => quit = true
        case _: KeyCode.Up =>
          modeUp()
          Results.updateRows()
        case _: KeyCode.Down =>
          modeDown()
          Results.updateRows()
        case c: KeyCode.Char =>
          mode match {
            case Mode.InputEdit  => FilterInput.addChar(c.c())
            case Mode.OutputEdit => FormatOutput.addChar(c.c())
            case _               =>
          }
        case _: KeyCode.Backspace =>
          mode match {
            case Mode.InputEdit  => FilterInput.removeChar()
            case Mode.OutputEdit => FormatOutput.removeChar()
            case _               =>
          }
        case _ =>
      }
    case _ =>
  }

  def run(): Unit = withTerminal { (jni, terminal) =>
    FilterInput.update()
    FormatOutput.update()
    Results.updateMatches()
    while (!quit) {
      terminal.draw { f =>
        draw(f)
      }
      handle(jni)
    }
  }
}
