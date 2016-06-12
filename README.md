KanjiVG Tools
=============

This project is a set of tools for [KanjiVG](http://kanjivg.tagaini.net/) data.
(See [KanjiVG GitHub repo](https://github.com/KanjiVG/kanjivg) for data and issues.)

It consists of:

- Parser for SVG files from KanjiVG
- Various validations of those SVG files
- (TODO) Transformation and fixing utilities for SVG files

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
