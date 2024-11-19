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
B_DESKTOP_FILE="bdeploy-${BDEPLOY_APP_UID}.desktop"
B_DESKTOP_UNINSTALL_FILE="bdeploy-uninstall-${BDEPLOY_APP_UID}.desktop"

cat > "${T_BDEPLOY_FILE}" <<EOF
{{BDEPLOY_FILE}}
EOF

# Check tooling
require_tool() {
   type "$1" > /dev/null 2>&1
   local S=$?
   if [[ ${S} -ne 0 ]]; then
      echo "This installer requires the '$1' command. Please install it. Cannot continue."
      if [[ -n "$2" ]]; then
        echo "(Hint: $2)"
      fi
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
HAVE_XDG_DESKTOP_MENU=$?
type xdg-desktop-icon > /dev/null
HAVE_XDG_DESKTOP_ICON=$?

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
  cert2="$(echo | openssl s_client -showcerts -connect $(echo "$url" | sed -e 's,.*/\([^/]*\)/api/*,\1,g') -prexit | openssl x509)"

  if [[ -z "$cert2" ]]; then
    echo "Cannot fetch server certificate, server not reachable?"
    exit 1
  fi

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
L_HOME="${B_HOME}/launcher"

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
    (
        cd "${T_UNZ}"
        echo "Unpacking launcher..."
        unzip "${T_DL}" > /dev/null

        echo "Installing launcher..."
        mv ${T_UNZ}/* ${L_HOME}

        # ATTENTION: Linux launcher ZIP misses executable bits at the moment.
        chmod +x ${L_HOME}/jre/bin/*
        chmod +x ${L_HOME}/bin/launcher
        chmod +x ${L_HOME}/bin/file-assoc.sh
        chmod +x ${L_HOME}/bin/autostart-launcher.sh

        rm -f ${T_DL}
    )

    if [[ ${HAVE_XDG_DESKTOP_MENU} == 0 ]]; then
        echo "Creating file association and autostart entry..."
        ${L_HOME}/bin/file-assoc.sh
        ${L_HOME}/bin/autostart-launcher.sh
    fi
fi

# make sure the launcher start scripts directory is in the PATH
(
    . ~/.bashrc
    if [[ "${PATH}" != *"${B_HOME}/apps/start_scripts"* ]]; then
        echo "Adding launcher to PATH in ~/.bashrc"
        cat >> ~/.bashrc << EOF

# Add BDeploy launcher installed applications to PATH
export PATH="\${PATH}:${B_HOME}/apps/start_scripts"
EOF
    fi
)

# stop here if only launcher install is requested
if [[ -z "${BDEPLOY_APP_UID}" ]]; then
    echo "Done installing launcher."
    exit 0
fi

# STEP 2: Find the icon file or download
if [[ -z "${SKIP_ICON}" ]]; then
    echo "Updating icon..."
    B_ICONS="${B_HOME}/apps/${BDEPLOY_APP_UID}"
    APP_ICON="${B_ICONS}/app.ico"
    APP_ICON_PNG="${B_ICONS}/app.png"
    if [[ -n "${BDEPLOY_ICON_URL}" && ${HAVE_XDG_DESKTOP_MENU} == 0 ]]; then
        require_tool convert "convert is part of the ImageMagick suite."
        require_tool identify "convert is part of the ImageMagick suite."

        # Icon URL set, need download
        mkdir -p "${B_ICONS}"
        rm -f "${APP_ICON}"
        dl "${BDEPLOY_ICON_URL}" "${APP_ICON}"

        T_CONV="${T}/icon"
        mkdir -p "${T_CONV}"
        (
            cd "${T_CONV}"
            convert "${APP_ICON}" "${T_CONV}/${BDEPLOY_APP_UID}.png"

            # produced multiple PNG's per ICO frame.
            largest=$(identify -format '%w %i\n' "${T_CONV}/${BDEPLOY_APP_UID}*.png" | sort -n | tail -n 1 | awk '{ print $2; }')
            if [[ ! -f "${largest}" ]]; then
                echo "Cannot find icon for application."
            else
                cp "${largest}" "${APP_ICON_PNG}"
            fi
        )
    fi
fi

# STEP 3: create application file
echo "Installing application file..."
B_APP_HOME="${B_HOME}/apps/${BDEPLOY_APP_UID}"
mkdir -p "${B_APP_HOME}"
cp "${T_BDEPLOY_FILE}" "${B_APP_HOME}"
mv "${B_APP_HOME}/${BDEPLOY_APP_UID}.bdeploy" "${B_APP_HOME}/launch.bdeploy"

# STEP 4: create uninstaller
echo "Creating uninstaller..."
B_UNINSTALLER="${B_APP_HOME}/uninstall.run"

echo '#!/usr/bin/env bash' > "${B_UNINSTALLER}"
# remove menu entry
if [[ ${HAVE_XDG_DESKTOP_MENU} == 0 ]]; then
    echo "xdg-desktop-menu uninstall ${B_DESKTOP_FILE}" >> "${B_UNINSTALLER}"
    echo "xdg-desktop-menu uninstall ${B_DESKTOP_UNINSTALL_FILE}" >> "${B_UNINSTALLER}"
fi
# remove desktop icon
if [[ ${HAVE_XDG_DESKTOP_ICON} == 0 ]]; then
    echo "xdg-desktop-icon uninstall ${B_DESKTOP_FILE}" >> "${B_UNINSTALLER}"
fi

# uninstall application
echo "${L_HOME}/bin/launcher uninstaller --app=${BDEPLOY_APP_UID}" >> "${B_UNINSTALLER}"

# Remove application directory
echo "rm -rf ${B_APP_HOME};" >> "${B_UNINSTALLER}"

# Make uninstaller executable
chmod +x "${B_UNINSTALLER}"

# STEP 5: create desktop entries
echo "Creating menu entries and desktop icon..."
T_BDEPLOY_LINK="${T}/bdeploy-folder.directory"
cat > "${T_BDEPLOY_LINK}" <<EOF
[Desktop Entry]
Version=1.0
Type=Directory
Name=BDeploy-Apps
Comment=BDeploy Applications
Icon=${L_HOME}/bin/logo128.png
EOF

T_LINK="${T}/${B_DESKTOP_FILE}"
cat > "${T_LINK}" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=${BDEPLOY_APP_NAME}
Comment=BDeploy Application: ${BDEPLOY_APP_NAME} (${BDEPLOY_APP_UID})
Exec=xdg-open ${B_APP_HOME}/launch.bdeploy
Icon=${APP_ICON_PNG}
Terminal=false
EOF

T_UNINSTALL_LINK="${T}/${B_DESKTOP_UNINSTALL_FILE}"
cat > "${T_UNINSTALL_LINK}" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=Uninstall_${BDEPLOY_APP_NAME}
Comment=Uninstall BDeploy Application: ${BDEPLOY_APP_NAME} (${BDEPLOY_APP_UID})
Exec=${B_UNINSTALLER}
Icon=${L_HOME}/bin/logo128.png
Terminal=false
EOF

if [[ ${HAVE_XDG_DESKTOP_MENU} == 0 ]]; then
    xdg-desktop-menu install "${T_BDEPLOY_LINK}" "${T_LINK}"
    xdg-desktop-menu install "${T_BDEPLOY_LINK}" "${T_UNINSTALL_LINK}"
    if [[ ${HAVE_XDG_DESKTOP_ICON} == 0 ]]; then
        xdg-desktop-icon install "${T_LINK}"
    fi
fi

# STEP 6: Launch
unatt=false
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --unattended) unatt=true ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

if [[ "$unatt" != true ]]; then
	echo "Launching ${BDEPLOY_APP_NAME}"
	${L_HOME}/bin/launcher "${B_APP_HOME}/launch.bdeploy"
else
	echo "Updating ${BDEPLOY_APP_NAME} in unattended mode"
	${L_HOME}/bin/launcher "${B_APP_HOME}/launch.bdeploy" "--updateOnly" "--unattended"
fi
