# Renamer

Simple TUI renamer app. Inspired by Total Commander's [multi rename tool](https://www.ghisler.ch/wiki/index.php/Multi-rename_tool).

![example.svg](example.svg)

## Installation with Coursier

```bash
coursier bootstrap \
  com.kubuszok:renamer_3:latest.release \
  -o renamer
```

### Native Image

```bash
# once coursier/coursier#3057 is fixed
coursier bootstrap --native-image \
  --graalvm graalvm-community:24.0.1 \
  com.kubuszok:renamer_3:latest.release \
  -o renamer \
  --graalvm-opt --no-fallback \
  -- \
    -H:IncludeResources=libnative-arm64-darwin-crossterm.dylib \
    -H:IncludeResources=libnative-x86_64-darwin-crossterm.dylib \
    -H:IncludeResources=libnative-x86_64-linux-crossterm.so \
    -H:IncludeResources=native-x86_64-windows-crossterm.dll
```

## Building

```bash
scala-cli --power package --native-image . -o renamer -- \
  -H:IncludeResources=libnative-arm64-darwin-crossterm.dylib \
  -H:IncludeResources=libnative-x86_64-darwin-crossterm.dylib \
  -H:IncludeResources=libnative-x86_64-linux-crossterm.so \
  -H:IncludeResources=native-x86_64-windows-crossterm.dll \
  --no-fallback
```

## Usage

Run `renamer` in the directory you want to rename things, then

 * edit `Filter` to select which files to edit (it's a regular expression)
 * edit `Rename to` to define how files should be renamed, you can use the following magic placeholders:
    * `[N]` - name without extension, you can select the substring with:
      * `[N2-5]` - filename without extension, characters from `2` to `5` (counting from 1)
      * `[N-2-5]` - filename without extension, characters from `2` from the end to `5` (counting from 1)
      * `[N2--5]` - filename without extension, characters from `2` to `5` from the end (counting from 1)
      * `[N2,5]` - filename without extension, `5` characters characters starting from `2` (counting from 1)
      * `[N-2,5]` - filename without extension, `5` characters characters starting from `2` from the end (counting from 1)
    * `[E]` - just extension, substrings working like with `N`
    * `[P]` - parent directory name, substrings working like with `N`
    * `[G]` - grandparent directory name, substrings working like with `N`
    * `[C]` - counter (which file it is), by default starting from `1`, by step `1`, without appending any zeroes, can be customized with:
      * `[C:10]` - counter starting from `10`
      * `[C;5]` - counter starting from `1` step by `5`
      * `[C:10;5]` - counter starting from `10` step by `5`
      * `[C%3]` - counter starting from `1` step by `1`, formatted with 3 digits
      * `[C:10;5%3]` - counter starting from `10` step by `5`, formatted with 3 digits
    * `[D:date format]` - date format [following Java's `DateTimeFormatter` convention](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html)
    * `$0`, `$1`, `$2` - groups from the regular expression match
      * Groups are extracted only from the first match! Use `^` and `$` if you want to match the whole line!
 * check if there are no issues, if it's OK, accept rename with `Enter`
 * or abort with `Esc`
