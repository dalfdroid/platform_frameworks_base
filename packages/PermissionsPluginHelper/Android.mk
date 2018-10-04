LOCAL_PATH:= $(call my-dir)

define do_gradle_build
$(shell cd $(1) && gradle --rerun-tasks build)
endef

include $(CLEAR_VARS)

GRADLE_BUILD_OUTPUT := $(call do_gradle_build,$(LOCAL_PATH))
ifeq (BUILD SUCCESSFUL, $(findstring BUILD SUCCESSFUL, $(GRADLE_BUILD_OUTPUT)))
$(info $(GRADLE_BUILD_OUTPUT))
else
$(info $(GRADLE_BUILD_OUTPUT))
$(error "Could not complete gradle build of the permissions plugin helper.")
endif

LOCAL_MODULE := permissionspluginhelper
LOCAL_MODULE_PATH := $(PRODUCT_OUT)/$(TARGET_COPY_OUT_SYSTEM)/framework
LOCAL_SRC_FILES := build/outputs/apk/release/PermissionsPluginHelper-release-unsigned.apk
LOCAL_CERTIFICATE := shared
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_SUFFIX := $(COMMON_JAVA_PACKAGE_SUFFIX)
include $(BUILD_PREBUILT)
