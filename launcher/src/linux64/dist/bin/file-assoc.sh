#!/usr/bin/env bash
# Creates a file association and the according desktop entry to the launcher
#
# Usage: ./file-assoc.sh "Action" "ID" "Extension" "Name of Application" "Exec-Path" "Icon"
#  Action: "add" or "remove"
#  ID: Unique ID of the application
#  Extension: file extension with ., e.g. ".txt"
#  Name of Application: human readable name of application
#  Exec-Path: full path to the script/executable to execute. The script will receive the file name to launch as last argument.
#  Icon: full path to a 128x128px icon in png format.

APP_HOME="${BDEPLOY_HOME:-${HOME}/.bdeploy}/launcher/bin"
LAUNCHER_PATH="${APP_HOME}/launcher"

ACTION="${1:-add}"
APP_ID="${2:-bdeploy}"
APP_EXT="${3:-.bdeploy}"
APP_NAME="${4:-BDeploy Click and Start Launcher}"
APP_EXEC="${5:-${LAUNCHER_PATH}}"
APP_ICON="${6}"

# This *INTENTIONALLY* conflicts with the .desktop file created by the installer.
# file-assoc is created later in the process and is more specific than the default
# .desktop file (adding mime-type).
TMP_MIME_DESC="${TMPDIR:-/tmp}/bdeploy-${APP_ID}.xml"
TMP_DESK_DESC="${TMPDIR:-/tmp}/bdeploy-${APP_ID}.desktop"

cat > ${TMP_MIME_DESC} <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
    <mime-type type="application/x-${APP_ID}">
        <comment>${APP_NAME}</comment>
        <glob pattern="*${APP_EXT}"/>
    </mime-type>
</mime-info>
EOF

if [[ -z "${APP_ICON}" || ! -e "${APP_ICON}" ]]; then
   APP_ICON="${APP_HOME}/logo128.png"
fi

cat > ${TMP_DESK_DESC} <<EOF
[Desktop Entry]
Version=1.0
Type=Application
MimeType=application/x-${APP_ID}
Name=${APP_NAME}
Comment=Installed via BDeploy
Icon=${APP_ICON}
TryExec=${APP_EXEC}
Exec=${APP_EXEC} %f
Terminal=false
EOF

if [[ ${ACTION} == "add" ]]; then
    xdg-mime install "${TMP_MIME_DESC}"
    xdg-icon-resource install --context mimetypes --size 128 "${APP_ICON}" application-x-${APP_ID}
    xdg-desktop-menu install "${TMP_DESK_DESC}"
    xdg-mime default bdeploy-${APP_ID}.desktop application/x-${APP_ID}
elif [[ ${ACTION} == "remove" ]]; then
    xdg-mime uninstall "${TMP_MIME_DESC}"
    xdg-icon-resource uninstall --context mimetypes --size 128 application-x-${APP_ID}
    xdg-desktop-menu uninstall "${TMP_DESK_DESC}"
fi

rm "${TMP_MIME_DESC}"
rm "${TMP_DESK_DESC}"
update-mime-database ~/.local/share/mime

