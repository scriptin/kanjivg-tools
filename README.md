KanjiVG Tools
=============

This project is a set of tools for [KanjiVG](http://kanjivg.tagaini.net/) data.
(See [KanjiVG GitHub repo](https://github.com/KanjiVG/kanjivg) for data and issues.)

It consists of:

- Parser for SVG files from KanjiVG
- Various validations of those SVG files
- Transformation and fixing utilities for SVG files

Building
--------

Requirements:

- JDK 1.8+ (tested with Oracle JDK)
- (optional) Gradle 2.13+ - project includes Gradle wrapper scripts (`gradlew` and `gradlew.bat`), you can just use those

Running
-------

On Mac and Linux:

    ./gradlew clean run -DkanjivgDir=/abs/path/to/kanjivg/kanji

On Windows:

    gradlew.bat clean run -DkanjivgDir=/abs/path/to/kanjivg/kanji

Arguments:

- `-Dkanjivg.dir=/abs/path/to/kanjivg/kanji` (required) - set absolute path to a directory with `*.svg` files from KanjiVG project
- `-Dlog.level=WARN` (optional) - set logging level:
    - `DEBUG` - most verbose, reports everything
    - `INFO` - reports progress, parsing/validation errors and application errors
    - `WARN` (default) - reports parsing/validation errors and application errors
    - `ERROR` least verbose, reports only application errors
- `-Dtask=validate` (optional) - task to run:
    - `validate` (default) - parse and validate files
    - `repair_ids` - fix invalid `id` attributes on all tags

### `validate` task arguments:

- `-Dvalidate.files.included=*` - list of glob-like patterns to filter files by their names w/o '.svg' extension,
  where `*` denotes any number of any characters
  Examples:
    - `-Dvalidate.files.included=*` (default) - matches all files
    - `-Dvalidate.files.included=01a2b` - matches a single file `01a2b.svg`
    - `-Dvalidate.files.included=01a2b*` - matches `01a2b.svg`, `01a2b-Kaisho.svg`, etc.
    - `-Dvalidate.files.included=01a2b,03c4d,05e6f` - matches `01a2b.svg`, `03c4d.svg`, `05e6f.svg`
- `-Dvalidate.files.excluded` - similar to previous, but for exclusions, `null` by default. Exclusions are applied *after* inclusions
- `-Dvalidate.validations.enabled=all` - enabled validations:
    - `all` (default) - all available validations
    - comma-separated list of case-sensitive names of validations from
      [org.kanjivg.tools.validation](tree/master/src/main/kotlin/org/kanjivg/tools/validation) package:
        - `WidthAndHeight`
        - `ViewBox`
        - `StrokeRootGroupId`
        - `StrokeRootGroupStyle`
        - `StrokeGroupsIds`
        - `StrokeIds`
        - `NumberRootGroupId`
        - `NumberRootGroupStyle`
        - `StrokeNumbersCount`
        - `NumberOrder`
        - `NumberPositions`
        - (this list may be outdated, please refer to the source code)

### `repair_ids` task arguments:

- `-Drepair_ids.files.included=*` - same as in the `validate` task
- `-Drepair_ids.files.excluded=*` - same as in the `validate` task

License
-------

This work is licensed under a [Creative Commons Attribution 4.0 International License][license].

[![Creative Commons Attribution 4.0 International License][license-img]][license]

  [license]: http://creativecommons.org/licenses/by/4.0/
  [license-img]: https://i.creativecommons.org/l/by/4.0/88x31.png
