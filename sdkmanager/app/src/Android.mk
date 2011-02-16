# Copyright 2007 The Android Open Source Project
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_JAVA_RESOURCE_DIRS := .

LOCAL_JAR_MANIFEST := ../etc/manifest.txt

# IMPORTANT: if you add a new dependency here, please make sure
# to also check the following files:
#   sdkmanager/app/etc/manifest.txt
#   sdkmanager/app/etc/android.bat
# (Note that we don't reference swt.jar in these files since
#  it is dynamically added by android.bat/.sh based on whether the
#  current VM is 32 or 64 bit.)
LOCAL_JAVA_LIBRARIES := \
	androidprefs \
	sdklib \
	sdkuilib \
	swt \
	org.eclipse.jface_3.4.2.M20090107-0800 \
	org.eclipse.equinox.common_3.4.0.v20080421-2006 \
	org.eclipse.core.commands_3.4.0.I20080509-2000

LOCAL_MODULE := sdkmanager

include $(BUILD_HOST_JAVA_LIBRARY)

