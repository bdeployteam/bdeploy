#!/usr/bin/env bash
echo "Command line arguments" $@
echo "Environment var TEST_VAR=${TEST_VAR}"

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
        --ping)
            echo "Pinging localhost endlessly"
            ping localhost
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
        --out=*)
            OUTFILE=${1##--out=}
            echo "Writing to file: ${OUTFILE}"
            echo "TEST" > "${OUTFILE}"
            ;;
        --subprocesses=*)
            PROCCNT=${1##--subprocesses=}
            echo "Starting ${PROCCNT} Sub-Processes..."
            while [[ ${PROCCNT} -gt 0 ]]; do
                sleep 60 &
                ((PROCCNT = PROCCNT - 1))
            done
            ;;
        --server=*)
            SRVPORT=${1##--server=}
            echo "Server port: ${SRVPORT}"
            ;;
        --client=*)
            CLIENTPORT=${1##--client=}
            echo "Client port: ${CLIENTPORT}"
            ;;
        *)
            echo "Unsupported argument: $1"
            exit 2
            ;;
    esac
    shift
done

exit 0

