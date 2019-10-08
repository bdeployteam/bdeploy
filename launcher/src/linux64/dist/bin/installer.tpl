#!/usr/bin/env bash

# This is the installer which is auto-populated with application specifics on download.

BDEPLOY_LAUNCHER_URL="{{LAUNCHER_URL}}"
BDEPLOY_ICON_URL="{{ICON_URL}}"
BDEPLOY_APP_UID="{{APP_UID}}"
BDEPLOY_APP_NAME="{{APP_NAME}}"

BDEPLOY_RS_URL="{{REMOTE_SERVICE_URL}}"
BDEPLOY_RS_TOKEN="{{REMOTE_SERVICE_TOKEN}}"

T="${TMPDIR:-/tmp}/bdeploy-$$"
mkdir -p "${T}"
trap "{ rm -rf ${T}; }" EXIT

T_BDEPLOY_FILE="${T}/${BDEPLOY_APP_UID}.bdeploy"

cat > "${T_BDEPLOY_FILE}" <<EOF
{{BDEPLOY_FILE}}
EOF

# Check tooling
require_tool() {
   type "$@" > /dev/null
   local S=$?
   if [[ ${S} -ne 0 ]]; then
      echo "This installer requires the '$@' command. Please install it. Cannot continue."
      exit 1
   fi
}

require_tool curl
require_tool unzip
require_tool sed
require_tool grep
require_tool cat
require_tool base64
require_tool openssl

type xdg-desktop-menu > /dev/null
HAVE_XDG_DESKTOP=$?

dl() {
  # find certificate from embedded JSON
  if [[ -z "${BDEPLOY_RS_URL}" ]]; then
      cert=$(cat "${T_BDEPLOY_FILE}" | grep authPack | sed -e 's,.*:[ \t]*"\([^"]*\)".*,\1,g' | base64 -d | grep '"c"' | sed -e 's,.*:[ \t]*"\([^"]*\)".*,\1,g')
      url=$(cat "${T_BDEPLOY_FILE}" | grep uri | sed -e 's,.*:[ \t]*"\([^"]*\)".*,\1,g')
  else
      cert=$(echo "${BDEPLOY_RS_TOKEN}" | base64 -d | grep '"c"' | sed -e 's,.*:[ \t]*"\([^"]*\)".*,\1,g')
      url="${BDEPLOY_RS_URL}"
  fi

  cat > "${T}/cert" <<EOF
-----BEGIN CERTIFICATE-----
$(echo $cert | awk '{gsub(/.{64}/, "&\n")}1')
-----END CERTIFICATE-----
EOF

  cert1="$(openssl x509 -in "${T}/cert")"
  cert2="$(echo | openssl s_client -showcerts -connect $(echo "$url" | sed -e 's,.*/\([^/]*\)/api/*,\1,g') -prexit 2>/dev/null | openssl x509)"

  if [[ "$cert1" != "$cert2" ]]; then
    echo "Certificate Mismatch"
    exit 1
  fi

  curl -sS -L -k "$1" --output "$2" > /dev/null
}

# Check that this file has been correctly pre-processed by the server
if [[ "${BDEPLOY_LAUNCHER_URL}" == "{""{"*"}""}" ]]; then
    echo "This file is internal to the BDeploy server, please do not use it directly!"
    exit 1
fi

# STEP 1: Find the launcher
B_HOME="${BDEPLOY_HOME:-${HOME}/.bdeploy}"
L_HOME="${B_HOME}/.launcher"

echo "Using BDEPLOY_HOME: ${B_HOME}..."
mkdir -p "${B_HOME}"
if [[ -d ${L_HOME} && -f ${L_HOME}/bin/launcher ]]; then
    # Launcher found.
    echo "Using existing launcher in ${L_HOME}..."
else
    # No Launcher, need download.
    rm -rf ${L_HOME}

    T_DL="${T}/launcher.zip"
    echo "Downloading launcher..."
    dl "${BDEPLOY_LAUNCHER_URL}" "${T_DL}"

    T_UNZ="${T}/launcher-unpack"
    mkdir "${T_UNZ}"
    cd "${T_UNZ}"
    echo "Unpacking launcher..."
    unzip "${T_DL}" > /dev/null

    echo "Installing launcher..."
    mv ${T_UNZ}/* ${L_HOME}

    # ATTENTION: Linux launcher ZIP misses executable bits at the moment.
    chmod +x ${L_HOME}/jre/bin/*
    chmod +x ${L_HOME}/bin/launcher
    chmod +x ${L_HOME}/bin/file-assoc.sh

    rm -f ${T_DL}

    if [[ ${HAVE_XDG_DESKTOP} == 0 ]]; then
        echo "Creating file association..."
        ${L_HOME}/bin/file-assoc.sh
    fi
fi

# stop here if only launcher install is requested
if [[ -z "${BDEPLOY_APP_UID}" ]]; then
    echo "Done installing launcher."
    exit 0
fi

# STEP 2: Find the icon file or download
echo "Updating icon..."
B_ICONS="${B_HOME}/.icons"
APP_ICON="${B_ICONS}/${BDEPLOY_APP_UID}.ico"
APP_ICON_PNG="${B_ICONS}/${BDEPLOY_APP_UID}.png"
if [[ -n "${BDEPLOY_ICON_URL}" && ${HAVE_XDG_DESKTOP} == 0 ]]; then
    require_tool convert
    require_tool identify

    # Icon URL set, need download
    mkdir -p "${B_ICONS}"
    rm -f "${APP_ICON}"
    dl "${BDEPLOY_ICON_URL}" "${APP_ICON}"

    T_CONV="${T}/icon"
    mkdir -p "${T_CONV}"
    cd "${T_CONV}"
    convert "${APP_ICON}" "${T_CONV}/${BDEPLOY_APP_UID}.png"

    # produced multiple PNG's per ICO frame.
    largest=$(identify -format '%w %i\n' "${T_CONV}/${BDEPLOY_APP_UID}*.png" | sort -n | tail -n 1 | awk '{ print $2; }')
    if [[ ! -f "${largest}" ]]; then
        echo "oups - cannot find icon for application."
    else
        cp "${largest}" "${APP_ICON_PNG}"
    fi
fi

# STEP 3: create desktop file for client for current user.
echo "Installing application shortcut..."
B_LAUNCHES_HOME="${B_HOME}/.launches"
mkdir -p "${B_LAUNCHES_HOME}"
cp "${T_BDEPLOY_FILE}" "${B_LAUNCHES_HOME}"

T_LINK="${T}/bdeploy-${BDEPLOY_APP_UID}.desktop"
cat > "${T_LINK}" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=${BDEPLOY_APP_NAME}
Comment=BDeploy Application: ${BDEPLOY_APP_NAME} (${BDEPLOY_APP_UID})
Exec=xdg-open ${B_LAUNCHES_HOME}/${BDEPLOY_APP_UID}.bdeploy
Icon=${APP_ICON_PNG}
Terminal=false
EOF

if [[ ${HAVE_XDG_DESKTOP} == 0 ]]; then
    xdg-desktop-menu install "${T_LINK}"
fi

# STEP 4: Launch directly.
echo "Launching ${BDEPLOY_APP_NAME}"
${L_HOME}/bin/launcher "${B_LAUNCHES_HOME}/${BDEPLOY_APP_UID}.bdeploy"

