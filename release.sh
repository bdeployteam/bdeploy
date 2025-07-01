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

if [[ -z "${GPG_SECRET_KEY}" ]]; then
    die "GPG_SECRET_KEY variable must contain the GPG secret key (path to file) which is used to sign artifacts"
fi

if [[ -z "${GPG_PUBLIC_KEY}" ]]; then
    die "GPG_PUBLIC_KEY variable must contain the GPG public key (path to file)"
fi

if [[ -z "${GPG_PASS}" ]]; then
    die "GPG_PASS variable must contain the passphrase for the GPG Key"
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
# this suffix is required by github actions, and we won't be able to download with this suffix attached, so we need to remove it.
LTS_SUFFIX=".0.LTS"
JAVA_FILE="$(cat .java-version)"
JAVA_REL=${JAVA_FILE%%${LTS_SUFFIX}}

if [[ -n "${JDK_DL_ROOT}" ]]; then
    r="${JDK_DL_ROOT}/jdks"
    if [[ -d "${r}" && -n "${JDK_DL_CLEAN}" ]]; then
        echo "Removing old JDKs in $r"
        rm -rf "${r}"
    fi

    if [[ ! -d "${r}/linux-x64" ]]; then
        mkdir -p "${r}/linux-x64"
        curl -L "https://api.adoptium.net/v3/binary/version/jdk-${JAVA_REL}/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" --output "${r}/jdk-linux-x64.tar.gz"
        (
            cd "${r}/linux-x64"
            tar xfz "${r}/jdk-linux-x64.tar.gz" > /dev/null
        )
    fi
    v=$(find "${r}/linux-x64/" -maxdepth 1 -type d -not -name 'linux-x64' -name '[^.]?*' -printf %f -quit)
    GRADLE_ARG_ARR+=( "-DlinuxX64jdk=${r}/linux-x64/${v}" )

    if [[ ! -d "${r}/win-x64" ]]; then
        mkdir -p "${r}/win-x64"
        curl -L "https://api.adoptium.net/v3/binary/version/jdk-${JAVA_REL}/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" --output "${r}/jdk-win-x64.zip"
        (
            cd "${r}/win-x64"
            unzip "${r}/jdk-win-x64.zip" > /dev/null
        )
    fi
    v=$(find "${r}/win-x64/" -maxdepth 1 -type d -not -name 'win-x64' -name '[^.]?*' -printf %f -quit)
    GRADLE_ARG_ARR+=( "-DwinX64jdk=${r}/win-x64/${v}" )

    if [[ ! -d "${r}/linux-aarch64" ]]; then
        mkdir -p "${r}/linux-aarch64"
        curl -L "https://api.adoptium.net/v3/binary/version/jdk-${JAVA_REL}/linux/aarch64/jdk/hotspot/normal/eclipse?project=jdk" --output "${r}/jdk-linux-aarch64.tar.gz"
        (
            cd "${r}/linux-aarch64"
            tar xfz "${r}/jdk-linux-aarch64.tar.gz" > /dev/null
        )
    fi
    v=$(find "${r}/linux-aarch64/" -maxdepth 1 -type d -not -name 'linux-aarch64' -name '[^.]?*' -printf %f -quit)
    GRADLE_ARG_ARR+=( "-DlinuxAarch64jdk=${r}/linux-aarch64/${v}" )
fi

GRADLE_ARG_ARR+=( "--stacktrace" )

[[ -f "${GPG_PUBLIC_KEY}" ]] || die "Public key not found at ${GPG_PUBLIC_KEY}"
[[ -f "${GPG_SECRET_KEY}" ]] || die "Public key not found at ${GPG_SECRET_KEY}"

./gradlew setVersion -PtargetVersion=$REL_VER "${GRADLE_ARG_ARR[@]}"
[[ -n "${NO_TESTS}" ]] && ./gradlew clean build release updateDocuScreenshots -x test -x runUitests "${GRADLE_ARG_ARR[@]}"
[[ -z "${NO_TESTS}" ]] && ./gradlew clean build release updateDocuScreenshots "${GRADLE_ARG_ARR[@]}"

export JRELEASER_GPG_PASSPHRASE="$GPG_PASS"
export JRELEASER_GPG_PUBLIC_KEY="$GPG_PUBLIC_KEY"
export JRELEASER_GPG_SECRET_KEY="$GPG_SECRET_KEY"

export JRELEASER_MAVENCENTRAL_USERNAME="$SONATYPE_USER"
export JRELEASER_MAVENCENTRAL_PASSWORD="$SONATYPE_TOKEN"

[[ -z "${NO_MAVEN}" ]] && ./gradlew publish jreleaserUpload "${GRADLE_ARG_ARR[@]}"

git add bdeploy.version doc
git commit -m "Release $REL_VER"
git push https://$GH_USER:$GH_TOKEN@github.com/bdeployteam/bdeploy.git HEAD:master

./gradlew githubRelease -PgithubToken=$GH_TOKEN "${GRADLE_ARG_ARR[@]}"
./gradlew setVersion -PtargetVersion=$NEXT_VER "${GRADLE_ARG_ARR[@]}"

git add bdeploy.version
git commit -m "Update to $NEXT_VER"

echo "Done."
