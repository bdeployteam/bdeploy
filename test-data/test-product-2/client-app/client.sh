#!/usr/bin/env bash

##
# Mini demo client application. opens the system text editor using xdg-open
##

# Current working directory is set by the launcher, so the file is in a "good" place.
cat > demo.txt <<EOF
BDeploy Launcher is working!

This file has been written by the demo client application on your system, and opened with your current text editor.
EOF

xdg-open demo.txt
