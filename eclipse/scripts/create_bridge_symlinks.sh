#!/bin/bash
function die() {
    echo "Error: $*"
    exit 1
}

set -e # fail early

# CD to the top android directory
D=`dirname "$0"`
cd "$D/../../../"

HOST=`uname`
if [ "$HOST" == "Linux" ]; then
    echo # nothing to do

elif [ "$HOST" == "Darwin" ]; then
    echo # nothing to do

elif [ "${HOST:0:6}" == "CYGWIN" ]; then
    if [ "x$1" == "x" ] || [ `basename "$1"` != "layoutlib.jar" ]; then
        echo "Usage: $0 [yoursdk/platforms/xxx/data/layoutlib.jar]"
        echo "WARNING: Argument 1 should be the SDK path to the layoutlib.jar to update."
    fi

    LIBS="layoutlib ninepatch"
    echo "Make java libs: $LIBS"
    make -j3 showcommands $LIBS || die "Bridge: Failed to build one of $LIBS."

    if [ "x$1" == "x" ] || [ `basename "$1"` != "layoutlib.jar" ]; then
        echo "Skip updating layoutlib.jar from an SDK"
    else
        echo "Updating your SDK in $1"
        cp -vf  "out/host/windows-x86/framework/layoutlib.jar" "$1"
        chmod -v a+rx "$1"
    fi

else
    echo "Unsupported platform ($HOST). Nothing done."
fi

