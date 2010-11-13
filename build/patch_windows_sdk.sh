#!/bin/bash

# This file is included by development.git/tools/build/patch_windows_sdk.sh.
# Please see the details in the other file.


# Verbose by default. Use -q to make more silent.
V="-v"
if [[ "$1" == "-q" ]]; then
  V=""
  shift
fi

TEMP_SDK_DIR=$1
WIN_OUT_DIR=$2
TOPDIR=${TOPDIR:-$3}

# Remove obsolete stuff from tools
TOOLS=$TEMP_SDK_DIR/tools
LIB=$TEMP_SDK_DIR/tools/lib
rm $V $TOOLS/{android,apkbuilder,ddms,draw9patch,emulator}
rm $V $TOOLS/{hierarchyviewer,layoutopt,mksdcard,traceview,monkeyrunner}
rm $V $TOOLS/proguard/bin/*.sh

# Copy all the new stuff in tools
# Note: keep this line here, just to remind us this is already done by the
# script in development.git/tools/build/patch_windows_sdk.sh. This will
# be obsolete when we switch to an .atree format.
# -- cp $V $WIN_OUT_DIR/host/windows-x86/bin/*.{exe,dll} $TOOLS/

# Copy the SDK Manager (aka sdklauncher) to the root of the SDK (it was copied in tools above)
# and move it also in SDK/tools/lib (so that tools updates can update the root one too)
cp $TOOLS/sdklauncher.exe $TEMP_SDK_DIR/"SDK Manager.exe"
mv $TOOLS/sdklauncher.exe $LIB/"SDK Manager.exe"

# Copy the emulator NOTICE in the tools dir
cp $V ${TOPDIR}external/qemu/NOTICE $TOOLS/emulator_NOTICE.txt

# Update a bunch of bat files
cp $V ${TOPDIR}sdk/files/post_tools_install.bat                 $LIB/
cp $V ${TOPDIR}sdk/files/find_java.bat                          $LIB/
cp $V ${TOPDIR}sdk/apkbuilder/etc/apkbuilder.bat                $TOOLS/
cp $V ${TOPDIR}sdk/ddms/app/etc/ddms.bat                        $TOOLS/
cp $V ${TOPDIR}sdk/traceview/etc/traceview.bat                  $TOOLS/
cp $V ${TOPDIR}sdk/hierarchyviewer2/app/etc/hierarchyviewer.bat $TOOLS/
cp $V ${TOPDIR}sdk/layoutopt/app/etc/layoutopt.bat              $TOOLS/
cp $V ${TOPDIR}sdk/draw9patch/etc/draw9patch.bat                $TOOLS/
cp $V ${TOPDIR}sdk/sdkmanager/app/etc/android.bat               $TOOLS/
cp $V ${TOPDIR}sdk/monkeyrunner/etc/monkeyrunner.bat            $TOOLS/
cp $V ${TOPDIR}sdk/files/proguard/bin/*.bat                     $TOOLS/proguard/bin/

