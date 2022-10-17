#include <jni.h>
#include <net/if.h>
#include <net/if_utun.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/kern_control.h>
#include <sys/socket.h>
#include <sys/sys_domain.h>
#include <unistd.h>

#include<stdio.h>

static jobject tun_address(JNIEnv *env, char* if_name) {
    jclass cls = (*env)->FindClass(env, "org/drasyl/channel/tun/TunAddress");
    jmethodID constructor = (*env)->GetMethodID(env, cls, "<init>", "(Ljava/lang/String;)V");
    jobject object = (*env)->NewObject(env, cls, constructor, (*env)->NewStringUTF(env, if_name));
    return object;
}

static jobject darwin_tun_device(JNIEnv *env, jint fd, jint mtu, jobject local_address) {
    jclass cls = (*env)->FindClass(env, "org/drasyl/channel/tun/darwin/DarwinTunDevice");
    jmethodID constructor = (*env)->GetMethodID(env, cls, "<init>", "(IILorg/drasyl/channel/tun/TunAddress;)V");
    jobject object = (*env)->NewObject(env, cls, constructor, fd, mtu, local_address);
    return object;
}

JNIEXPORT jobject JNICALL Java_org_drasyl_channel_tun_darwin_Native_open(JNIEnv *env, jclass clazz, jint index, jint mtu) {
    // create socket
    int fd = socket(AF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL);

    if (fd == -1) {
        perror("socket(...)");
        return NULL;
    }

    // mark socket as utun device
    struct ctl_info ctlInfo;
    memset(&ctlInfo, 0, sizeof(ctlInfo));
    if (strlcpy(ctlInfo.ctl_name, UTUN_CONTROL_NAME, sizeof(ctlInfo.ctl_name)) >= sizeof(ctlInfo.ctl_name)) {
        fprintf(stderr, "UTUN_CONTROL_NAME too long");
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
        perror("connect(...)");
        goto error;
    }

    // get socket name
    char sockName[IFNAMSIZ];
    int sockNameLen = IFNAMSIZ;
    if (getsockopt(fd, SYSPROTO_CONTROL, UTUN_OPT_IFNAME, sockName, (uint32_t*) &sockNameLen) == -1) {
        perror("getsockopt(...)");
        goto error;
    }

    // mtu
    struct ifreq ifr;
    strncpy(ifr.ifr_name, sockName, IFNAMSIZ);
    if (mtu != 0) {
        // set mtu
        ifr.ifr_mtu = mtu;
        if (ioctl(fd, SIOCSIFMTU, &ifr) == -1) {
            perror("ioctl(SIOCSIFMTU)");
            goto error;
        }
    }
    else {
        // get mtu
        if (ioctl(fd, SIOCGIFMTU, &ifr) == -1) {
           perror("ioctl(SIOCGIFMTU)");
           goto error;
        }
        mtu = ifr.ifr_ifru.ifru_mtu;
    }

    jobject tun_addr = tun_address(env, sockName);
    jobject darwin_tun_dev = darwin_tun_device(env, fd, mtu, tun_addr);

    return darwin_tun_dev;
error:
    close(fd);
    return NULL;
}

JNIEXPORT jint JNICALL Java_org_drasyl_channel_tun_darwin_Native_close(JNIEnv *env, jclass clazz, jint fd) {
    return close(fd);
}

JNIEXPORT jint JNICALL Java_org_drasyl_channel_tun_darwin_Native_read(JNIEnv *env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    void* buffer = (*env)->GetDirectBufferAddress(env, jbuffer);

    return read(fd, buffer + pos, (size_t) (limit - pos));
}

JNIEXPORT jlong JNICALL Java_org_drasyl_channel_tun_darwin_Native_readAddress(JNIEnv *env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    void* buffer = (void *) (intptr_t) address;
//    printf("READ fd = %i\n", fd);
//    printf("READ buffer = %p\n", buffer);
//    printf("READ buffer + pos = %p\n", buffer + pos);

    return read(fd, buffer + pos, (size_t) (limit - pos));
}

JNIEXPORT jint JNICALL Java_org_drasyl_channel_tun_darwin_Native_write(JNIEnv *env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    void* buffer = (*env)->GetDirectBufferAddress(env, jbuffer);
//    printf("WRITE fd           = %i\n", fd);
//    printf("WRITE jbuffer      = %p\n", jbuffer);
//    printf("WRITE buffer       = %p\n", buffer);
//    printf("WRITE buffer + pos = %p\n", buffer + pos);
//    printf("WRITE limit        = %i\n", limit);
//    printf("WRITE pos          = %i\n", pos);
//    printf("WRITE limit - pos  = %lu\n", (size_t) (limit - pos));

    int res = write(fd, buffer + pos, (size_t) (limit - pos));
    if (res == -1) {
        perror("write(...)");
    }

    return res;
}

JNIEXPORT jint JNICALL Java_org_drasyl_channel_tun_darwin_Native_writeAddress(JNIEnv *env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    void* buffer = (void *) (intptr_t) address;

    return write(fd, buffer + pos, (size_t) (limit - pos));
}

//JNIEXPORT jlong JNICALL Java_org_drasyl_channel_tun_darwin_Native_writevAddresses(JNIEnv *env, jclass clazz, jint fd, jlong memoryAddress, jint length) {
//    struct iovec* iov = (struct iovec*) (intptr_t) memoryAddress;
//
//    ssize_t res;
//    int err;
//    do {
//        res = writev(fd, iov, length);
//        // keep on writing if it was interrupted
//    } while (res == -1 && ((err = errno) == EINTR));
//
//    if (res < 0) {
//        return -err;
//    }
//    return (jlong) res;
//}
