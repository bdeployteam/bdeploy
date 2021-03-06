#!/usr/bin/env bash
echo "Command line arguments" $@

while [[ $# -gt 0 ]]; do
    case $1 in
        --help) 
            echo "This is very helpful"
            exit 1
            ;;
        --sleep=*)
            TIMEOUT=${1##--sleep=}
            echo "Sleeping some (${TIMEOUT})"
            sleep ${TIMEOUT}
            ;;
        --text=*)
            TEXT=${1##--text=}
            echo "Got some text: ${TEXT}"
            ;;
        --cfg=*)
            CFGFILE=${1##--cfg=}
            echo "Got config file: ${CFGFILE}"
            cat "${CFGFILE}"
            ;;
        *)
            echo "Unsupported argument: $1"
            exit 2
            ;;
    esac
    shift
done

exit 0

