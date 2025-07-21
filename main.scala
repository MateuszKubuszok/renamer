package renamer

@main def main: Unit = {
  // Vector(
  //   SubString.From(5) -> "abdc",
  //   SubString.From(5) -> "abcdefghijk",
  //   SubString.FromTo(5, 8) -> "abcdefghijk",
  //   SubString.FromTo(5, -2) -> "abcd",
  //   SubString.FromTo(5, -2) -> "abcdefghijk",
  //   SubString.FromTo(-5, -2) -> "abcdefghijk",
  //   SubString.DropTake(5, 2) -> "abcd",
  //   SubString.DropTake(5, 2) -> "abcdefghijk",
  //   SubString.DropTake(-3, 2) -> "abcd",
  //   SubString.DropTake(-5, 2) -> "abcdefghijk",
  // ).foreach { (s, a) =>
  //   println(s"$s($a) = ${s(a)} ")
  // }
  // Vector(
  //   "dupa",
  //   "dupa [N]",
  //   "dupa [N0,10]",
  //   "dupa [N0-10] [E0,10]",
  //   "dupa [N-0,10] [E0,10",
  // ).map(a => a -> Converter.parse(a)).foreach(println)

  App(FileInfo.fromPWD).run()
}
