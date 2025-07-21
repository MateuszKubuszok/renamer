package renamer

@main def main: Unit =
  System.exit(App(FileInfo.fromPWD).run())
