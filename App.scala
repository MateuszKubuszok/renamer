package renamer

import java.io.File
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.collection.immutable.ListMap
import scala.util.boundary
import scala.util.matching.Regex

import tui.*
import tui.crossterm.{ Color => _, * }
import tui.widgets.*

final class App(files: Array[FileInfo]) {

  // Mode

  private enum Mode {
    case InputEdit
    case OutputEdit
    case MatchPreview(selected: FileInfo)
    case Execute(head: Results.Match, tail: List[Results.Match], done: Int, total: Int)
    case Cancel
  }
  private object Mode {

    val current = ReactiveVariable(Mode.InputEdit)

    def up(): Unit = current.value = current.value match {
      case Mode.InputEdit  => Mode.InputEdit
      case Mode.OutputEdit => Mode.InputEdit
      case Mode.MatchPreview(selected) =>
        Results.previousMatch(selected) match {
          case Some(prev) => Mode.MatchPreview(prev)
          case None       => Mode.OutputEdit
        }
      case mode => mode
    }

    def down(): Unit = current.value = current.value match {
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
      case mode => mode
    }

    def cancel(): Unit = current.value = Mode.Cancel

    def execute(): Unit = if (Errors.errors.value.isEmpty && FormatOutput.rawPattern.value.nonEmpty) {
      Results.matches.value.toList match {
        case head :: tail => current.value = Mode.Execute(head, tail, 0, tail.length + 1)
        case Nil          => current.value = Mode.Cancel
      }
    }
  }

  // UI

  val headerStyle   = Style.DEFAULT.addModifier(Modifier.BOLD)
  val selectedStyle = Style.DEFAULT.fg(Color.Blue).addModifier(Modifier.BOLD)
  val invalidStyle  = Style.DEFAULT.fg(Color.Red)

  private object FilterInput {

    val rawPattern = ReactiveVariable("")
    val parsedPattern = rawPattern.map { raw =>
      if (raw.isEmpty) Right(Regex(".*"))
      else
        try
          Right(Regex(raw))
        catch {
          case _: Throwable => Left(s"Invalid regex: $raw")
        }
    }
    val pattern = parsedPattern.map {
      case Right(pattern) => pattern
      case Left(_)        => Regex(".*")
    }
    val isSelected = Mode.current.map {
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
        style = if (parsedPattern.value.isRight) Style.DEFAULT else invalidStyle,
        wrap = None,
        alignment = Alignment.Left
      )

      parent.renderWidget(input, rect)
    }
  }

  private object FormatOutput {

    val rawPattern      = ReactiveVariable("")
    val parsedConverter = rawPattern.map(Converter.parse)
    val converter = parsedConverter.map {
      case Right(converter) => converter
      case Left(_)          => Converter.Noop
    }
    val isSelected = Mode.current.map {
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
        style = if (parsedConverter.value.isRight) Style.DEFAULT else invalidStyle,
        wrap = None,
        alignment = Alignment.Left
      )

      parent.renderWidget(input, rect)
    }
  }

  private object Results {

    final case class Match(
      input:  FileInfo,
      output: Path,
      groups: Array[String]
    ) {

      def describe: String = s"Renaming: ${input.nameExt}  -> ${output.getFileName.toString}"
    }

    val matches = FilterInput.pattern.map2(FormatOutput.converter) { (pattern, converter) =>
      files.view
        .flatMap { fi =>
          pattern.findFirstMatchIn(fi.nameExt).map { m =>
            fi -> (0 to m.groupCount).map(m.group(_)).toArray
          }
        }
        .zipWithIndex
        .map { case ((fi, groups), index) =>
          val output = converter(fi, index, groups)
          Match(fi, output, groups)
        }
        .toArray
    }
    val selected = Mode.current.map {
      case Mode.MatchPreview(selected) => Some(selected)
      case _                           => None
    }
    val isSelected = selected.map(_.isDefined)
    val rows = matches.map2(selected) { (matches, selectedFile) =>
      matches.map { case Match(input, output, values) =>
        TableWidget.Row(
          cells = Array(
            TableWidget.Cell(Text.nostyle(input.nameExt)),
            TableWidget.Cell(Text.nostyle(input.date.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))),
            TableWidget.Cell(Text.nostyle(output.getFileName.toString))
          ),
          height = 1,
          style = if (selectedFile.contains(input)) selectedStyle.addModifier(Modifier.REVERSED) else Style.DEFAULT
        )
      }
    }
    val validated = matches.map { values =>
      val countByName = values.groupMapReduce(_.output.getFileName.toString)(_ => 1)(_ + _)
      val duplicates  = countByName.filter(_._2 > 1).map(_._1).toVector.sorted
      if (duplicates.isEmpty) Right(())
      else Left("Duplicated names after rename: " + duplicates.mkString(", "))
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
        TableWidget.Cell(Text.from(Spans.styled("Date", headerStyle))),
        TableWidget.Cell(Text.from(Spans.styled("Renamed", headerStyle)))
      )
    )

    private val state = TableWidget.State()

    def render(parent: Frame, rect: Rect) = {
      val highlighedRowPos = selected.value.map(s => matches.value.indexWhere(r => r.input == s))

      val shownRows = highlighedRowPos
        .map { pos =>
          val visibleRows = rect.height - 2 - 2 // 2 for borders, 2 for ???
          val rowsSize    = matches.value.length
          rows.value.drop(if (pos > visibleRows) pos - visibleRows else 0)
        }
        .getOrElse(rows.value)

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
        widths = Array(Constraint.Percentage(40), Constraint.Min(25), Constraint.Percentage(40)),
        highlightStyle = Style(addModifier = Modifier.REVERSED),
        highlightSymbol = Some("*"),
        header = Some(header),
        rows = shownRows
      )

      parent.renderStatefulWidget(filesTable, rect)(state)
    }
  }

  object Errors {

    val errors = ReactiveValue.from(FilterInput.parsedPattern, FormatOutput.parsedConverter, Results.validated) { (filter, converter, validated) =>
      filter.left.toOption.toVector ++ converter.left.toOption.toVector ++ validated.left.toOption.toVector
    }

    def render(parent: Frame, rect: Rect) = {
      val style = if (errors.value.isEmpty) Style.DEFAULT else invalidStyle
      val output = ParagraphWidget(
        text =
          if (errors.value.isEmpty) Text.nostyle("No issues")
          else Text.from(Span.styled(errors.value.mkString(", "), invalidStyle)),
        block = Some(
          BlockWidget(
            title = Some(Spans.nostyle("Issues")),
            titleAlignment = Alignment.Left,
            borders = Borders.ALL,
            borderType = BlockWidget.BorderType.Rounded,
            borderStyle = style,
            style = style
          )
        ),
        style = style,
        wrap = None,
        alignment = Alignment.Left
      )

      parent.renderWidget(output, rect)
    }
  }

  private def drawConfig(f: Frame): Unit = {
    val rects = Layout(
      direction = Direction.Vertical,
      constraints = Array(Constraint.Length(3), Constraint.Length(3), Constraint.Min(10), Constraint.Length(3)),
      margin = Margin(1)
    ).split(f.size)
    FilterInput.render(f, rects(0))
    FormatOutput.render(f, rects(1))
    Results.render(f, rects(2))
    Errors.render(f, rects(3))
  }

  def handleInput(jni: CrosstermJni): Unit = jni.read() match {
    case key: Event.Key =>
      key.keyEvent.code match {
        case _: KeyCode.Esc  => Mode.cancel()
        case _: KeyCode.Up   => Mode.up()
        case _: KeyCode.Down => Mode.down()
        case c: KeyCode.Char =>
          Mode.current.value match {
            case Mode.InputEdit  => FilterInput.addChar(c.c())
            case Mode.OutputEdit => FormatOutput.addChar(c.c())
            case _               =>
          }
        case _: KeyCode.Backspace =>
          Mode.current.value match {
            case Mode.InputEdit  => FilterInput.removeChar()
            case Mode.OutputEdit => FormatOutput.removeChar()
            case _               =>
          }
        case _: KeyCode.Enter => Mode.execute()
        case _ =>
      }
    case mouse: Event.Mouse =>
      mouse.mouseEvent.kind match {
        case _: MouseEventKind.ScrollUp   => Mode.up()
        case _: MouseEventKind.ScrollDown => Mode.down()
        case _ =>
      }
    case _ =>
  }

  def drawProgressBar(f: Frame, current: String, done: Int, total: Int): Unit = {
    val progress = done.toDouble / total.toDouble

    val rects = Layout(
      direction = Direction.Vertical,
      constraints = Array(Constraint.Percentage(49), Constraint.Length(3)),
      margin = Margin(1),
      expandToFill = false
    ).split(f.size)

    val width = rects(0).width - 2
    val bar   = "█" * (width * progress).toInt

    val output = ParagraphWidget(
      text = Text.nostyle(bar),
      block = Some(
        BlockWidget(
          title = Some(Spans.nostyle(current)),
          titleAlignment = Alignment.Left,
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded,
          borderStyle = Style.DEFAULT,
          style = Style.DEFAULT
        )
      ),
      style = Style.DEFAULT,
      wrap = None,
      alignment = Alignment.Left
    )

    f.renderWidget(output, rects(1))
  }

  private def executeNext(head: Results.Match, tail: List[Results.Match], done: Int, total: Int): Option[Int] = {
    val Results.Match(input, output, values) = head
    val in                                   = input.file
    val out                                  = output.toFile
    if (!in.exists() || out.exists()) Some(1)
    else if (!in.renameTo(out)) Some(-1)
    else {
      tail match {
        case head :: tail =>
          Mode.current.value = Mode.Execute(head, tail, done + 1, total)
          None
        case Nil =>
          Some(0)
      }
    }
  }

  def run(): Int = withTerminal { (jni, terminal) =>
    @scala.annotation.tailrec
    def loop(): Int = Mode.current.value match {
      case Mode.InputEdit | Mode.OutputEdit | Mode.MatchPreview(_) =>
        terminal.draw(drawConfig)
        handleInput(jni)
        loop()
      case Mode.Execute(head, tail, done, total) =>
        terminal.draw(drawProgressBar(_, head.describe, done, total))
        executeNext(head, tail, done, total) match {
          case Some(exitCode) => exitCode
          case None           => loop()
        }
      case Mode.Cancel =>
        0
    }

    loop()
  }
}
