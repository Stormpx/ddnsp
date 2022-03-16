package io.crowds;

import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class Platform {


    public static DatagramChannel getDatagramChannel(){
        if (Epoll.isAvailable()){
            return new EpollDatagramChannel();
        }else{
            return new NioDatagramChannel();
        }
    }

    public static Class<? extends DatagramChannel> getDatagramChannelClass(){
        if (Epoll.isAvailable()){
            return EpollDatagramChannel.class;
        }else{
            return NioDatagramChannel.class;
        }
    }

    public static Class<? extends ServerSocketChannel> getServerSocketChannelClass(){
        if (Epoll.isAvailable()){
            return EpollServerSocketChannel.class;
        }else{
            return NioServerSocketChannel.class;
        }
    }


    public static Class<? extends SocketChannel> getSocketChannelClass(){
        if (Epoll.isAvailable()){
            return EpollSocketChannel.class;
        }else{
            return NioSocketChannel.class;
        }
    }
}
