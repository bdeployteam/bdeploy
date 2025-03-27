#!/usr/bin/env bash

# Steps to create a BDeploy release.
#
# Requires two arguments: the version to release and the new version number after the release.
#
# Following environment variables are supported:
#  GH_USER (required): the username to use when pushing or publishing a release.
#  GH_TOKEN (required): the token to use when pushing or publishing a release. GH_USER and GH_TOKEN must match.
#  NO_TESTS (optional): whether to skip tests.
#  JDK_DL_ROOT (optional): directory where JDKs should be downloaded automatically. If not set, JDKs must be setup correctly on the machine in gradle.properties.
#  JDK_DL_CLEAN (optional): remove existing JDKs and re-download the current latest version. If a JDK already exists, it will be re-used no matter what version if not cleaning.
#  GRADLE_ARGS (optional): additional gradle arguments

ROOT=$(cd $(dirname $0); pwd)
cd "$ROOT"

REL_VER="$1"
NEXT_VER="$2"
GRADLE_ARG_ARR=( $GRADLE_ARGS )

die() {
    echo "ERROR:" "$@" 1>&2
    exit 1
}

if [[ -z "${REL_VER}" ]]; then
    die "First argument must be the version to release"
fi

if [[ -z "${NEXT_VER}" ]]; then
    die "Second argument must be the next version to use"
fi

if [[ -z "${GH_USER}" ]]; then
    die "GH_USER variable must contain the GitHub user which is allowed to push and create a release"
fi

if [[ -z "${GH_TOKEN}" ]]; then
    die "GH_TOKEN variable must contain the GitHub token which is allowed to create a release"
fi

if [[ -z "${SONATYPE_USER}" ]]; then
    die "SONATYPE_USER variable must contain the Nexus user which is allowed to push and create a release"
fi

if [[ -z "${SONATYPE_TOKEN}" ]]; then
    die "SONATYPE_TOKEN variable must contain the Nexus token which is allowed to create a release"
fi

if [[ -z "${GPG_ID}" ]]; then
    die "GPG_ID variable must contain the GPG Key which is used to signe artifacts"
fi

if [[ -z "${GPG_PASS}" ]]; then
    die "GPG_PASS variable must contain the passphrase for the GPG Key"
fi

if [[ -z "${GPG_FILE}" ]]; then
    die "GPG_FILE variable must contain the path to the GPG Keyring file"
fi


CURRENT_VER="$(cat "$ROOT/bdeploy.version")"

if [[ ${CURRENT_VER} != *"-SNAPSHOT" ]]; then
    die "Current version ($CURRENT_VER) is not a SNAPSHOT version!"
fi

if [[ ${NEXT_VER} != *"-SNAPSHOT" ]]; then
    die "Next version ($NEXT_VER) must be a SNAPSHOT version!"
fi

if [[ ! ${REL_VER} =~ ^[0-9]+.[0-9]+.[0-9]+$ ]]; then
    die "Release version ($REL_VER) format does not match MAJOR.MINOR.MICRO"
fi

echo "Releasing $REL_VER from current $CURRENT_VER, updating to $NEXT_VER. OK?"
read ok

set -e

if [[ -n "${JDK_DL_ROOT}" ]]; then
    r="${JDK_DL_ROOT}/jdks"
    if [[ -d "${r}" && -n "${JDK_DL_CLEAN}" ]]; then
        echo "Removing old JDKs in $r"
        rm -rf "${r}"
    fi

    if [[ ! -d "${r}/linux64" ]]; then
        mkdir -p "${r}/linux64"
        curl -L "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/adoptium?project=jdk" --output "${r}/jdk-linux64.tar.gz"
        (
            cd "${r}/linux64"
            tar xfz "${r}/jdk-linux64.tar.gz" > /dev/null
        )
    fi
    v=$(find "${r}/linux64/" -maxdepth 1 -type d -not -name 'linux64' -name '[^.]?*' -printf %f -quit)
    GRADLE_ARG_ARR+=( "-Dlinux64jdk=${r}/linux64/${v}" )

    if [[ ! -d "${r}/win64" ]]; then
        mkdir -p "${r}/win64"
        curl -L "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/adoptium?project=jdk" --output "${r}/jdk-win64.zip"
        (
            cd "${r}/win64"
            unzip "${r}/jdk-win64.zip" > /dev/null
        )
    fi
    v=$(find "${r}/win64/" -maxdepth 1 -type d -not -name 'win64' -name '[^.]?*' -printf %f -quit)
    GRADLE_ARG_ARR+=( "-Dwin64jdk=${r}/win64/${v}" )
fi

./gradlew setVersion -PtargetVersion=$REL_VER "${GRADLE_ARG_ARR[@]}"
[[ -n "${NO_TESTS}" ]] && ./gradlew clean build release updateDocuScreenshots -x test -x runUitests "${GRADLE_ARG_ARR[@]}"
[[ -z "${NO_TESTS}" ]] && ./gradlew clean build release updateDocuScreenshots "${GRADLE_ARG_ARR[@]}"

[[ -z "${NO_MAVEN}" ]] && ./gradlew publish -PsonatypeUser=$SONATYPE_USER -PsonatypeToken=$SONATYPE_TOKEN -Psigning.keyId=$GPG_ID -Psigning.password=$GPG_PASS -Psigning.secretKeyRingFile=$GPG_FILE "${GRADLE_ARG_ARR[@]}"

git add bdeploy.version doc
git commit -m "Release $REL_VER"
git push https://$GH_USER:$GH_TOKEN@github.com/bdeployteam/bdeploy.git HEAD:master

./gradlew githubRelease -PgithubToken=$GH_TOKEN "${GRADLE_ARG_ARR[@]}"
./gradlew setVersion -PtargetVersion=$NEXT_VER "${GRADLE_ARG_ARR[@]}"
[[ -z "${NO_TESTS}" ]] && ./gradlew build releaseTest -x test -x runUitests "${GRADLE_ARG_ARR[@]}"

git add bdeploy.version
git commit -m "Update to $NEXT_VER"

echo "Done."
