package io.crowds.lib.unix;

//struct pollfd {
//   int   fd;         /* file descriptor */
//   short events;     /* requested events */
//   short revents;    /* returned events */
//};
public class PollFd {
    private int fd;
    private short events;
    private short revents;

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }

    public short getEvents() {
        return events;
    }

    public void setEvents(short events) {
        this.events = events;
    }

    public short getRevents() {
        return revents;
    }

    public void setRevents(short revents) {
        this.revents = revents;
    }
}
