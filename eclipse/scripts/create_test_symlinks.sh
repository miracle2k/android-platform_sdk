#!/bin/bash

set -e

# CD to the top android directory
D=`dirname "$0"`
cd "$D/../../../"

function die() {
    echo "Error: $*"
    exit 1
}

# computes relative ".." paths from $1 to here (in /android)
function back() {
  echo $1 | sed 's@[^/]*@..@g'
}

HOST=`uname`
if [ "${HOST:0:6}" == "CYGWIN" ]; then
    # We can't use symlinks under Cygwin
    function cpdir() { # $1=dest $2=source
        echo "rsync $2 => $1"
        rsync -avW --delete-after $2 $1
    }

else
    # For all other systems which support symlinks
    function cpdir() { # $1=dest $2=source
        ln -svf `back $1`/$2 $1
    }
fi

BASE="sdk/eclipse/plugins/com.android.ide.eclipse.tests"
DEST=$BASE
BACK=`back $DEST`

LIBS="easymock"

echo "make java libs ..."
make -j3 showcommands $LIBS || die "ADT: Fail to build one of $LIBS."

echo "Copying java libs to $DEST"

HOST=`uname`
if [ "$HOST" == "Linux" ]; then
    for LIB in $LIBS; do
        ln -svf $BACK/out/host/linux-x86/framework/$LIB.jar "$DEST/"
    done
    ln -svf $BACK/out/host/linux-x86/framework/kxml2-2.3.0.jar        "$DEST/"

elif [ "$HOST" == "Darwin" ]; then
    for LIB in $LIBS; do
        ln -svf $BACK/out/host/darwin-x86/framework/$LIB.jar "$DEST/"
    done
    ln -svf $BACK/out/host/darwin-x86/framework/kxml2-2.3.0.jar       "$DEST/"

elif [ "${HOST:0:6}" == "CYGWIN" ]; then
    for LIB in $LIBS; do
        cp -vf  out/host/windows-x86/framework/$LIB.jar "$DEST/"
    done
    if [ ! -f "$DEST/kxml2-2.3.0.jar" ]; then
        cp -v "prebuilt/common/kxml2/kxml2-2.3.0.jar" "$DEST/"
    fi

    chmod -v a+rx "$DEST"/*.jar
else
    echo "Unsupported platform ($HOST). Nothing done."
fi

# Cleanup old obsolete symlink

function clean() {
  if [[ -e "$1" || -L "$1" ]]; then
    rm -rfv "$1"
  fi
}

DEST=$BASE/unittests/com/android
clean $DEST/sdkuilib
clean $DEST/ddmlib
clean $DEST/sdklib

DEST=$BASE/unittests/com/android/layoutlib
clean $DEST/bridge
clean $DEST/testdata

