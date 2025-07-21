package renamer

import java.io.File
import java.nio.file.Path
import java.time.Instant

final case class FileInfo(
  nameExt: String,
  path:    Path,
  file:    File
) {

  lazy val name = {
    val idx = nameExt.lastIndexOf('.')
    if (idx == -1) nameExt
    else nameExt.substring(0, idx)
  }

  lazy val extension = {
    val idx = nameExt.lastIndexOf('.')
    if (idx == -1) ""
    else nameExt.substring(idx + 1)
  }

  lazy val parent      = Option(path.getParent).map(_.getFileName.toString).getOrElse("")
  lazy val grandParent = Option(path.getParent).flatMap(p => Option(p.getParent)).map(_.getFileName.toString).getOrElse("")

  lazy val date = Instant.ofEpochMilli(file.lastModified())
}
object FileInfo {

  def fromPWD: Array[FileInfo] = {
    val pwd = File(".")
    File(".").listFiles.map { file =>
      val path = file.toPath
      val name = path.getFileName.toString
      FileInfo(name, path, file)
    }
  }
}
