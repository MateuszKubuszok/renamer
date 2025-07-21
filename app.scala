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

  private val mode = ReactiveVariable(Mode.InputEdit)

  private def modeUp(): Unit = mode.value = mode.value match {
    case Mode.InputEdit  => Mode.InputEdit
    case Mode.OutputEdit => Mode.InputEdit
    case Mode.MatchPreview(selected) =>
      Results.previousMatch(selected) match {
        case Some(prev) => Mode.MatchPreview(prev)
        case None       => Mode.OutputEdit
      }
  }

  private def modeDown(): Unit = mode.value = mode.value match {
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

  private object FilterInput {

    private val rawPattern = ReactiveVariable("")
    private val parsedPattern = rawPattern.map { raw =>
      if (raw.isEmpty) Some(Regex(".*"))
      else
        try
          Some(Regex(raw))
        catch {
          case _: Throwable => None
        }
    }
    val pattern = parsedPattern.map {
      case Some(pattern) => pattern
      case None          => Regex(".*")
    }
    private val isSelected = mode.map {
      case Mode.InputEdit => true
      case _              => false
    }

    def addChar(c: Char): Unit =
      rawPattern.value += c

    def removeChar(): Unit =
      if (rawPattern.value.nonEmpty) {
        rawPattern.value = rawPattern.value.dropRight(1)
      }

    def render(parent: Frame, rect: Rect) = {
      val input = ParagraphWidget(
        text = Text.nostyle(rawPattern.value),
        block = Some(
          BlockWidget(
            title = Some(Spans.nostyle("Filter")),
            titleAlignment = Alignment.Left,
            borders = Borders.ALL,
            borderType = BlockWidget.BorderType.Rounded,
            borderStyle = if (isSelected.value) selectedStyle else Style.DEFAULT,
            style = Style.DEFAULT
          )
        ),
        style = if (parsedPattern.value.isDefined) Style.DEFAULT else invalidStyle,
        wrap = None,
        alignment = Alignment.Left
      )

      parent.renderWidget(input, rect)
    }
  }

  object FormatOutput {

    private val rawPattern = ReactiveVariable("")
    private val parsedConverter = rawPattern.map { raw =>
      Converter.parse(raw).toOption
    }
    val converter = parsedConverter.map {
      case Some(converter) => converter
      case None            => Converter.Noop
    }
    private val isSelected = mode.map {
      case Mode.OutputEdit => true
      case _               => false
    }

    def addChar(c: Char): Unit =
      rawPattern.value += c

    def removeChar(): Unit =
      if (rawPattern.value.nonEmpty) {
        rawPattern.value = rawPattern.value.dropRight(1)
      }

    def render(parent: Frame, rect: Rect) = {
      val input = ParagraphWidget(
        text = Text.nostyle(rawPattern.value),
        block = Some(
          BlockWidget(
            title = Some(Spans.nostyle("Rename to")),
            titleAlignment = Alignment.Left,
            borders = Borders.ALL,
            borderType = BlockWidget.BorderType.Rounded,
            borderStyle = if (isSelected.value) selectedStyle else Style.DEFAULT,
            style = Style.DEFAULT
          )
        ),
        style = if (parsedConverter.value.isDefined) Style.DEFAULT else invalidStyle,
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

    private val matches = FilterInput.pattern.map2(FormatOutput.converter) { (pattern, converter) =>
      files.filter(f => pattern.findFirstIn(f.name).isDefined).map { case fi @ FileInfo(name, path, file) =>
        val output = converter(fi)
        // TODO
        Match(fi, output, ListMap.empty)
      }
    }
    private val isSelected = mode.map {
      case Mode.MatchPreview(_) => true
      case _                    => false
    }
    private val rows = matches.map2(mode) { (matches, mode) =>
      val selectedFile = mode match {
        case Mode.MatchPreview(selected) => selected
        case _                           => null
      }
      matches.map { case Match(input, output, values) =>
        TableWidget.Row(
          cells = Array(TableWidget.Cell(Text.nostyle(input.nameExt)), TableWidget.Cell(Text.nostyle(output.getFileName.toString))),
          height = 1,
          style = if (input == selectedFile) selectedStyle else Style.DEFAULT
        )
      }
    }

    def headOption: Option[FileInfo] = matches.value.headOption.map(_.input)

    def previousMatch(selected: FileInfo): Option[FileInfo] = {
      val idx = matches.value.indexWhere(_.input == selected)
      if (idx <= 0) None
      else Some(matches.value(idx - 1).input)
    }

    def nextMatch(selected: FileInfo): Option[FileInfo] = {
      val idx = matches.value.indexWhere(_.input == selected)
      if (idx >= matches.value.length - 1) None
      else Some(matches.value(idx + 1).input)
    }

    private val header = TableWidget.Row(
      cells = Array(
        TableWidget.Cell(Text.from(Spans.styled("Name", headerStyle))),
        TableWidget.Cell(Text.from(Spans.styled("Renamed", headerStyle)))
      )
    )

    private val state = TableWidget.State()

    def render(parent: Frame, rect: Rect) = {
      val filesTable = TableWidget(
        block = Some(
          BlockWidget(
            title = Some(Spans.nostyle("Files")),
            titleAlignment = Alignment.Left,
            borders = Borders.ALL,
            borderType = BlockWidget.BorderType.Rounded,
            borderStyle = if (isSelected.value) selectedStyle else Style.DEFAULT,
            style = Style.DEFAULT
          )
        ),
        widths = Array(Constraint.Percentage(50), Constraint.Length(30), Constraint.Min(10)),
        highlightStyle = Style(addModifier = Modifier.REVERSED),
        highlightSymbol = Some("*"),
        header = Some(header),
        rows = rows.value
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
        case _: KeyCode.Esc  => quit = true
        case _: KeyCode.Up   => modeUp()
        case _: KeyCode.Down => modeDown()
        case c: KeyCode.Char =>
          mode.value match {
            case Mode.InputEdit  => FilterInput.addChar(c.c())
            case Mode.OutputEdit => FormatOutput.addChar(c.c())
            case _               =>
          }
        case _: KeyCode.Backspace =>
          mode.value match {
            case Mode.InputEdit  => FilterInput.removeChar()
            case Mode.OutputEdit => FormatOutput.removeChar()
            case _               =>
          }
        case _ =>
      }
    case _ =>
  }

  def run(): Unit = withTerminal { (jni, terminal) =>
    while (!quit) {
      terminal.draw { f =>
        draw(f)
      }
      handle(jni)
    }
  }
}
