package renamer

import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale
import scala.util.matching.Regex

enum SubString {
  case Whole
  case From(from: Int)
  case FromTo(from: Int, to: Int)
  case DropTake(drop: Int, take: Int)

  def apply(s: String): String = {
    def normalizeFrom(from: Int): Option[Int] =
      if (from.abs > s.length) None
      else Some((if (from < 0) s.length + from else from - 1).min(s.length - 1))
    def normalizeTo(to: Int): Option[Int] =
      if (to.abs >= s.length) None
      else Some((if (to < 0) s.length + to else to).min(s.length))
    this match {
      case Whole => s
      case From(from0) =>
        normalizeFrom(from0).fold("")(s.substring(_))
      case FromTo(from, to) =>
        normalizeFrom(from).zip(normalizeTo(to)).map { case (from, to) => s.substring(from, to) }.getOrElse("")
      case DropTake(drop, take) =>
        normalizeFrom(drop)
          .flatMap { from =>
            val to = from + take
            if (to > s.length) None
            else Some(s.substring(from, to))
          }
          .getOrElse("")
    }
  }
}
object SubString {

  private val from     = "(.*)(-?[0-9]+)-".r
  private val fromTo   = "(.*)(-?[0-9]+)-(-?[0-9]+)".r
  private val dropTake = "(.*)(-?[0-9]+),([0-9]+)".r
  private val whole    = "([NEPG]?)".r

  def parseNameWithSubString(s: String): Option[(String, SubString)] = s match {
    case from(prefix, from)           => Some(prefix -> From(from.toInt))
    case fromTo(prefix, from, to)     => Some(prefix -> FromTo(from.toInt, to.toInt))
    case dropTake(prefix, drop, take) => Some(prefix -> DropTake(drop.toInt, take.toInt))
    case whole(prefix)                => Some(prefix -> Whole)
    case _                            => None
  }
}

enum Conversion {
  case NameExt(substring: SubString)
  case Name(substring: SubString)
  case Ext(substring: SubString)
  case Parent(substring: SubString)
  case GrandParent(substring: SubString)
  case Date(format: DateTimeFormatter)
  case Raw(pattern: String)

  def apply(file: FileInfo): String = this match {
    case NameExt(substring)     => substring(file.nameExt)
    case Name(substring)        => substring(file.name)
    case Ext(substring)         => substring(file.extension)
    case Parent(substring)      => substring(file.parent)
    case GrandParent(substring) => substring(file.grandParent)
    case Date(format)           => format.format(file.date)
    case Raw(pattern)           => pattern
  }
}
object Conversion {

  private val lb = Regex.quote("[")
  private val rb = Regex.quote("]")

  private val placeholder = s"$lb([^\\[]+)$rb(.*)".r
  private val raw         = s"([^\\[]+)(.*)".r

  def parseNext(s: String): Either[String, (Conversion, String)] = s match {
    case placeholder(name, rest) =>
      SubString
        .parseNameWithSubString(name)
        .collect {
          case ("N", substring) => Name(substring) -> rest
          case ("E", substring) => Ext(substring) -> rest
          case ("P", substring) => Parent(substring) -> rest
          case ("G", substring) => GrandParent(substring) -> rest
          case ("", substring)  => NameExt(substring) -> rest
        }
        .map(Right(_))
        .getOrElse {
          name match {
            case s"D:$format" =>
              try
                Right(Date(DateTimeFormatter.ofPattern(format).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())) -> rest)
              catch {
                case _: Throwable => Left(s"Invalid date format: $format")
              }
            case _ => Left(s"Invalid placeholder format: $name")
          }
        }
    case raw(raw, rest) => Right(Raw(raw) -> rest)
    case rest           => Right(Raw(rest) -> "")
  }
}

sealed trait Converter {

  def apply(file: FileInfo): Path
}
object Converter {

  case object Noop extends Converter {
    def apply(file: FileInfo): Path = file.path
  }

  final case class Parsed(conversions: Vector[Conversion]) extends Converter {

    import Conversion.*
    def apply(file: FileInfo): Path = Path.of(conversions.map(_(file)).mkString)
  }

  def parse(s: String): Either[String, Converter] = {
    def loop(remaining: String, acc: Vector[Conversion]): Either[String, Vector[Conversion]] =
      if (remaining.isEmpty) Right(acc)
      else {
        Conversion.parseNext(remaining) match {
          case Right((conversion, rest)) =>
            assert(rest.length < remaining.length, s"Expected that rest=$rest is shorter than remaining=$remaining")
            loop(rest, acc :+ conversion)
          case Left(error) => Left(error)
        }
      }

    if (s.isEmpty) Right(Noop)
    else loop(s, Vector.empty).map(Parsed(_))
  }
}
