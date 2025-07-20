package renamer

import java.io.File
import java.nio.file.Path

final case class FileInfo(
  name: String,
  path: Path,
  file: File
)
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