package io.crowds.compoments.xdp.event;

public interface FdHandle {

    int fd();

    void doRead();

    void doWrite();

    default void post(){}
}
