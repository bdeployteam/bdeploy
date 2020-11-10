#!/usr/bin/env bash
# Creates a file association and the according desktop entry to the launcher

APP_HOME="${BDEPLOY_HOME:-${HOME}/.bdeploy}/launcher/bin"
LAUNCHER_PATH="${APP_HOME}/launcher"

TMP_MIME_DESC="${TMPDIR:-/tmp}/bdeploy-bdeploy.xml"
TMP_DESK_DESC="${TMPDIR:-/tmp}/bdeploy-launcher.desktop"

cat > ${TMP_MIME_DESC} <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
    <mime-type type="application/x-bdeploy">
        <comment>A BDeploy launcher descriptor</comment>
        <glob pattern="*.bdeploy"/>
    </mime-type>
</mime-info>
EOF

cat > ${TMP_DESK_DESC} <<EOF
[Desktop Entry]
Version=1.0
Type=Application
MimeType=application/x-bdeploy
Name=BDeploy Click & Start launcher
Comment=Interpreter for .bdeploy files
Icon=${APP_HOME}/logo128.png
TryExec=${LAUNCHER_PATH}
Exec=${LAUNCHER_PATH} %f
Terminal=false
EOF

xdg-mime install "${TMP_MIME_DESC}"
xdg-icon-resource install --context mimetypes --size 64 "${APP_HOME}/logo64.png" application-x-bdeploy
xdg-icon-resource install --context mimetypes --size 128 "${APP_HOME}/logo128.png" application-x-bdeploy
xdg-desktop-menu install "${TMP_DESK_DESC}"
xdg-mime default bdeploy-launcher.desktop application/x-bdeploy

rm "${TMP_MIME_DESC}"
rm "${TMP_DESK_DESC}"
