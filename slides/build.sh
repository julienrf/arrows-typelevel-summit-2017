#!/bin/sh

OUT=index.html
SRC=slides.md

build() {
  echo "Compiling slides…"
  pandoc --standalone --slide-level=3 --number-sections --variable slidy-url=slidy --smart -r markdown -w slidy -o $OUT $SRC
}

stop_server() {
    if [ -f .server_pid ]; then kill $(cat .server_pid) && rm .server_pid; fi
}

start_server() {
  python3 -m http.server &
  echo $! > .server_pid
}

restart_server() {
  stop_server
  start_server
}

stop_and_exit() {
  stop_server
  exit
}

case "$1" in
    watch)
        trap stop_and_exit 2
        build
        restart_server
        while true; do
            change=$(inotifywait -q -e close $SRC)
            build
        done
        ;;
    publish)
        build
        cp -R slidy ~/sites/julienrf.github.com/2017/arrows/
        cp $OUT *.svg *.png ~/sites/julienrf.github.com/2017/arrows/
        ;;
    stop-server)
        stop_server
        ;;
    *)
        build
        ;;
esac
