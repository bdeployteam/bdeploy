#!/usr/bin/env bash

APP_HOME="${BDEPLOY_HOME:-${HOME}/.bdeploy}/launcher/bin"
LAUNCHER_PATH="${APP_HOME}/launcher"

TMP_AUTO_DESC="${TMPDIR:-/tmp}/bdeploy-autostart.desktop"
AUTO_DESC_TARGET="${XDG_CONFIG_HOME:-${HOME}/.config}/autostart"

cat > ${TMP_AUTO_DESC} <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=BDeploy Autostart
Comment=Autostart applications installed using BDeploy
Icon=${APP_HOME}/logo128.png
TryExec=${LAUNCHER_PATH}
Exec=${LAUNCHER_PATH} autostart
Terminal=false
EOF

mv "${TMP_AUTO_DESC}" "${AUTO_DESC_TARGET}"

