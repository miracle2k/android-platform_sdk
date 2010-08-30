LOCAL_PATH:= $(call my-dir)

file := $(TARGET_OUT_KEYLAYOUT)/AVRCP.kl
ALL_PREBUILT += $(file)
$(file) : $(LOCAL_PATH)/AVRCP.kl | $(ACP)
	$(transform-prebuilt-to-target)
