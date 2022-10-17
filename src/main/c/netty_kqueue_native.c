/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
#include <net/if.h>
#include <net/if_utun.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/kern_control.h>
#include <sys/socket.h>
#include <sys/sys_domain.h>
#include <unistd.h>

#include "netty_unix_errors.h"
#include "netty_unix_jni.h"
#include "netty_unix_util.h"
#include "netty_unix.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define STATICALLY_CLASSNAME "org/drasyl/channel/tun/darwin/DarwinStaticallyReferencedJniMethods"
#define NATIVE_CLASSNAME "org/drasyl/channel/tun/darwin/Native"

static jclass tunAddressClass = NULL;
static jmethodID tunAddressMethodId = NULL;
static jclass tunDeviceClass = NULL;
static jmethodID tunDeviceMethodId = NULL;
static const char* staticPackagePrefix = NULL;
static int register_unix_called = 0;

static jobject netty_kqueue_native_open(JNIEnv* env, jclass clazz, jint index, jint mtu) {
    // create socket
    int fd = socket(AF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL);

    if (fd == -1) {
        netty_unix_errors_throwIOException(env, "socket() failed");
        return NULL;
    }

    // mark socket as utun device
    struct ctl_info ctlInfo;
    memset(&ctlInfo, 0, sizeof(ctlInfo));
    if (strlcpy(ctlInfo.ctl_name, UTUN_CONTROL_NAME, sizeof(ctlInfo.ctl_name)) >= sizeof(ctlInfo.ctl_name)) {
        netty_unix_errors_throwIOException(env, "UTUN_CONTROL_NAME too long");
        goto error;
    }
    if (ioctl(fd, CTLIOCGINFO, &ctlInfo) == -1) {
        perror("ioctl(...)");
        goto error;
    }

    // define address of socket
    struct sockaddr_ctl address;
    address.sc_id = ctlInfo.ctl_id;
    address.sc_len = sizeof(address);
    address.sc_family = AF_SYSTEM;
    address.ss_sysaddr = AF_SYS_CONTROL;
    address.sc_unit = 0;
    if (connect(fd, (struct sockaddr *)&address, sizeof(address)) == -1) {
        netty_unix_errors_throwIOException(env, "connect() failed");
        goto error;
    }

    // get socket name
    char sockName[IFNAMSIZ];
    int sockNameLen = IFNAMSIZ;
    if (getsockopt(fd, SYSPROTO_CONTROL, UTUN_OPT_IFNAME, sockName, (uint32_t*) &sockNameLen) == -1) {
        netty_unix_errors_throwIOException(env, "getsockopt() failed");
        goto error;
    }

    // mtu
    struct ifreq ifr;
    strncpy(ifr.ifr_name, sockName, IFNAMSIZ);
    if (mtu != 0) {
        // set mtu
        ifr.ifr_mtu = mtu;
        if (ioctl(fd, SIOCSIFMTU, &ifr) == -1) {
            netty_unix_errors_throwIOException(env, "ioctl(SIOCSIFMTU) failed");
            goto error;
        }
    }
    else {
        // get mtu
        if (ioctl(fd, SIOCGIFMTU, &ifr) == -1) {
           netty_unix_errors_throwIOException(env, "ioctl(SIOCGIFMTU) failed");
           goto error;
        }
        mtu = ifr.ifr_ifru.ifru_mtu;
    }

    jobject tun_addr = (*env)->NewObject(env, tunAddressClass, tunAddressMethodId, (*env)->NewStringUTF(env, sockName));
    jobject darwin_tun_dev = (*env)->NewObject(env, tunDeviceClass, tunDeviceMethodId, fd, mtu, tun_addr);

    return darwin_tun_dev;
error:
    close(fd);
    return NULL;
}

static void netty_kqueue_native_noop(JNIEnv* env, jclass clazz) {
    // noop
}

static jint netty_kqueue_native_registerUnix(JNIEnv* env, jclass clazz) {
    register_unix_called = 1;
    return netty_unix_register(env, staticPackagePrefix);
}

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "open", "(II)Lorg/drasyl/channel/tun/darwin/DarwinTunDevice;", (void *) netty_kqueue_native_open },
  { "noop", "()V", (void *) netty_kqueue_native_noop },
  { "registerUnix", "()I", (void *) netty_kqueue_native_registerUnix }
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);
// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Native to reflect that.
static jint netty_kqueue_native_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int staticallyRegistered = 0;
    int nativeRegistered = 0;

    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STATICALLY_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto error;
    }
    staticallyRegistered = 1;

    // Register the methods which are not referenced by static member variables
    if (netty_jni_util_register_natives(env, packagePrefix, NATIVE_CLASSNAME, fixed_method_table, fixed_method_table_size) != 0) {
        goto error;
    }
    nativeRegistered = 1;

    // Initialize this module

    NETTY_JNI_UTIL_LOAD_CLASS(env, tunAddressClass, "org/drasyl/channel/tun/TunAddress", error);
    NETTY_JNI_UTIL_GET_METHOD(env, tunAddressClass, tunAddressMethodId, "<init>", "(Ljava/lang/String;)V", error);

    NETTY_JNI_UTIL_LOAD_CLASS(env, tunDeviceClass, "org/drasyl/channel/tun/darwin/DarwinTunDevice", error);
    NETTY_JNI_UTIL_GET_METHOD(env, tunDeviceClass, tunDeviceMethodId, "<init>", "(IILorg/drasyl/channel/tun/TunAddress;)V", error);

    staticPackagePrefix = packagePrefix;

    return NETTY_JNI_UTIL_JNI_VERSION;
error:
   if (staticallyRegistered == 1) {
        netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
   }
   if (nativeRegistered == 1) {
        netty_jni_util_unregister_natives(env, packagePrefix, NATIVE_CLASSNAME);
   }
   return JNI_ERR;
}

static void netty_kqueue_native_JNI_OnUnload(JNIEnv* env) {
    if (register_unix_called == 1) {
        register_unix_called = 0;
        netty_unix_unregister(env, staticPackagePrefix);
    }

    NETTY_JNI_UTIL_UNLOAD_CLASS(env, tunAddressClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, tunDeviceClass);

    netty_jni_util_unregister_natives(env, staticPackagePrefix, STATICALLY_CLASSNAME);
    netty_jni_util_unregister_natives(env, staticPackagePrefix, NATIVE_CLASSNAME);

    if (staticPackagePrefix != NULL) {
        free((void *) staticPackagePrefix);
        staticPackagePrefix = NULL;
    }
}

// We build with -fvisibility=hidden so ensure we mark everything that needs to be visible with JNIEXPORT
// https://mail.openjdk.java.net/pipermail/core-libs-dev/2013-February/014549.html

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_transport_native_kqueue(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_kqueue", netty_kqueue_native_JNI_OnLoad);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_transport_native_kqueue(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_kqueue_native_JNI_OnUnload);
}

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_kqueue", netty_kqueue_native_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_kqueue_native_JNI_OnUnload);
}
#endif /* NETTY_BUILD_STATIC */
