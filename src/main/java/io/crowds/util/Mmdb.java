package io.crowds.util;

import com.maxmind.db.ClosedDatabaseException;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Mmdb {
    private final static Logger logger= LoggerFactory.getLogger(Mmdb.class);
    private static Mmdb instance;
    private final Vertx vertx;
    private final HttpClient httpClient;

    private long duration;
    private TimeUnit timeUnit;

    private String target;

    private Long timerId;
    private volatile DatabaseReader database=null;

    public Mmdb(Vertx vertx, long duration, TimeUnit timeUnit) {
        Objects.requireNonNull(vertx);
        this.vertx = vertx;
        this.duration = duration;
        this.timeUnit = timeUnit;
        this.httpClient=vertx.createHttpClient(new HttpClientOptions()
                .setShared(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2));
    }



    public static Mmdb instance(){
        return instance;
    }

    public static Mmdb initialize(Vertx vertx, long duration, TimeUnit timeUnit){
        if (Mmdb.instance!=null){
            return Mmdb.instance;
        }
        Mmdb.instance = new Mmdb(vertx,duration,timeUnit);
        return Mmdb.instance;
    }

    private void setReloadTimer(){
        if (timerId!=null) {
            vertx.cancelTimer(timerId);
        }

        timerId = vertx.setTimer(timeUnit.toMillis(duration),id->{
            logger.info("try reload mmdb target: {}",target);
            load(target);
            setReloadTimer();
        });

    }

    private void replaceDatabase(DatabaseReader reader) throws IOException {
        DatabaseReader database = this.database;
        this.database=reader;
        if (database !=null){
            database.close();
        }
        setReloadTimer();
    }

    public Future<Void> load(String target){
        if (Objects.equals(this.target,target)){
            return Future.succeededFuture();
        }
        boolean http=false;
        URI uri=null;
        try {
            uri = URI.create(target);
            http=uri.isAbsolute()&&uri.getScheme().startsWith("http");
        } catch (Exception e) {
        }
        if (http){
            return httpClient.request(new RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setAbsoluteURI(target)
                    .setFollowRedirects(true))
                    .compose(HttpClientRequest::send)
                    .compose(response->{
                        if (response.statusCode()!=200)
                            return Future.failedFuture("statuscode = "+response.statusCode());
                        return response.body();
                    })
                    .<Void>compose(buffer->{
                        try {
                            load(new ByteBufInputStream(((BufferImpl)buffer).byteBuf()));
                            return Future.succeededFuture(null);
                        } catch (Exception e) {
                            return Future.failedFuture(e);
                        }
                    })
                    .onFailure(e->{
                        logger.error("load mmdb failed. because: {} {}",e.getCause().getClass(),e.getMessage());
                    })
                    .onSuccess(v->this.target=target);


        }else{
            try {
                load(Path.of(target));
                this.target=target;
                return Future.succeededFuture();
            } catch (Exception e) {
                logger.error("load mmdb failed. because: {} {}",e.getCause().getClass(),e.getMessage());
                return Future.failedFuture(e);
            }
        }
    }

    public void load(InputStream inputStream){
        try {
            DatabaseReader reader = new DatabaseReader.Builder(inputStream).build();
            replaceDatabase(reader);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(),e);
        }
    }

    public void load(Path path){
        try {
            if (!Files.exists(path)){
                throw new RuntimeException(path+" not exists");
            }
            if (!Files.isRegularFile(path)){
                throw new RuntimeException(path+" is not a regular file");
            }
            DatabaseReader reader = new DatabaseReader.Builder(path.toFile()).build();
            replaceDatabase(reader);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(),e);
        }
    }

    public String queryIsoCode(InetAddress address){
        if (database==null)
            return null;
        if (address==null)
            return null;
        try {
            CountryResponse response = database.tryCountry(address).orElse(null);
            if (response==null)
                return null;
            return response.getCountry().getIsoCode();
        }catch (ClosedDatabaseException e){
            return null;
        } catch (IOException | GeoIp2Exception e) {
            throw new RuntimeException(e.getMessage(),e);
        }

    }


}
