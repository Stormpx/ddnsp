package io.crowds.lib.unix;

import io.crowds.util.Native;
import top.dreamlike.panama.generator.annotation.NativeFunction;
import top.dreamlike.panama.generator.proxy.NativeArray;


public interface Poll {
    Poll INSTANCE = Native.nativeGenerate(Poll.class);

    int POLLIN =   0x001;
    int POLLPRI =  0x002;
    int POLLOUT =  0x004;

    /**
     * int poll(struct pollfd *fds, nfds_t nfds, int timeout);
     */
    @NativeFunction(needErrorNo = true)
    int poll(NativeArray<PollFd> fds, int nfds, int timeout);

}
