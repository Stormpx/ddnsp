package io.crowds.compoments.xdp.event;

import io.crowds.lib.unix.Poll;
import io.netty.channel.IoEvent;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoRegistration;
import io.netty.channel.epoll.EpollIoEvent;
import io.netty.channel.epoll.EpollIoHandle;
import io.netty.channel.epoll.EpollIoOps;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.concurrent.Future;

import java.util.concurrent.ExecutionException;

public class EpollFdHandler implements FdHandler{

    private final IoEventLoop eventLoop;

    public EpollFdHandler(IoEventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public FdRegistration register(FdHandle handle) {
        try {
            EpollHandle epollHandle = new EpollHandle(handle);
            Future<IoRegistration> future = eventLoop.register(epollHandle);
            future.sync();
            return new EpollRegistration(future.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    final class EpollHandle implements EpollIoHandle{
        private final FdHandle handle;

        EpollHandle(FdHandle handle) {
            this.handle = handle;
        }

        @Override
        public FileDescriptor fd() {
            return new FileDescriptor(handle.fd());
        }

        @Override
        public void handle(IoRegistration ioRegistration, IoEvent ioEvent) {
            EpollIoEvent event = (EpollIoEvent) ioEvent;
            if (event.ops().contains(EpollIoOps.EPOLLIN)){
                handle.doRead();
            }
            if (event.ops().contains(EpollIoOps.EPOLLOUT)){
                handle.doWrite();
            }
            handle.post();
        }

        @Override
        public void close() throws Exception {
//            handle.close();
        }
    }

    final class EpollRegistration implements FdRegistration {

        private final IoRegistration registration;

        EpollRegistration(IoRegistration registration) {
            this.registration = registration;
        }

        @Override
        public void submit(int interestEvents) {
            EpollIoOps ops = EpollIoOps.NONE;
            if ((interestEvents & Poll.POLLIN) != 0 ){
                ops = ops.with(EpollIoOps.EPOLLIN);
            }
            if ((interestEvents & Poll.POLLOUT) != 0 ){
                ops = ops.with(EpollIoOps.EPOLLOUT);
            }
            registration.submit(ops);
        }

        @Override
        public void cancel() {
            registration.cancel();
        }
    }
}
