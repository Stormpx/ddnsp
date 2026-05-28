package io.crowds.compoments.partial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.socket.SegmentTapper;
import org.stormpx.net.util.IPPort;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SegmentLogger implements SegmentTapper {

    private static final Logger logger = LoggerFactory.getLogger(SegmentLogger.class);
    private final List<TappedSegment> buffer = new ArrayList<>();
    private Path tmpPath;
    private FileWriter fileWriter;
    private boolean writeHeader;
    private boolean closed;

    //segment state
    private TappedSegment firstSegment;
    private IPPort local;
    private IPPort remote;

    private void createFile() throws IOException {
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir")).resolve("partialTcp");
        if (!Files.exists(tmpDir)){
            Files.createDirectories(tmpDir);
        }
        Path path = Files.createTempFile(tmpDir,"tcp-", ".csv");
        this.fileWriter = new FileWriter(path.toFile());
        this.tmpPath = path;
    }

    private void writeHeader() throws IOException {
        var header = "timestamp,rtt,seq,ack,flight,cwnd,ssthresh\n";
        this.fileWriter.write(header);
        this.writeHeader = true;
    }

    private long calcOffset(long t1,long t2){
        return (int)(t1-t2);
    }

    private String toCsv(TappedSegment segment){
        if (this.firstSegment == null){
            this.firstSegment = segment;
            this.local = segment.local();
            this.remote = segment.remote();
        }
        var line = new StringBuilder()
                .append(calcOffset(segment.time(),this.firstSegment.time())).append(",")
                .append(segment.rtt()).append(",")
                .append(calcOffset(segment.seqNumber(),this.firstSegment.seqNumber())).append(",")
                .append(calcOffset(segment.ackNumber(),this.firstSegment.ackNumber())).append(",")
                .append(segment.inflight()).append(",")
                .append(segment.cwnd()).append(",")
                .append(segment.ssthresh())
                .toString();
        return line;
    }

    private void flush() throws IOException {
        if (fileWriter==null){
            createFile();
        }
        if (!writeHeader){
            writeHeader();
        }
        for (var data : buffer) {
            this.fileWriter.write(toCsv(data));
            this.fileWriter.write("\n");
        }
        this.buffer.clear();
    }

    void close(){
        if (closed){
            return;
        }
        try {
            this.closed = true;
            flush();
            if (this.fileWriter!=null) {
                this.fileWriter.close();
                logger.debug("{}-{} logs: {}",remote,local,tmpPath.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void OnReceived(TappedSegment segment) {
        if (closed){
            return;
        }
        try {

            buffer.add(segment);

            if (this.buffer.size()>2048){
                flush();
            }
            if (segment.isRst()||segment.isFin()){
                close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
