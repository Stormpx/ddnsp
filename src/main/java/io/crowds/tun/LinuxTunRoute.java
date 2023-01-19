package io.crowds.tun;

import io.crowds.util.IPCIDR;
import io.crowds.util.Rands;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.concurrent.atomic.AtomicInteger;

public class LinuxTunRoute implements TunRoute{
    private final static AtomicInteger TABLE_ID = new AtomicInteger(Rands.nextInt(4000,9000));
    private String dev;
    private String ip;
    private int mask;
    private String table;

    public LinuxTunRoute(String dev, IPCIDR ipcidr) {
        this.dev = dev;
        this.ip=ipcidr.getAddress().getHostAddress();
        this.mask= ipcidr.getMask();
        this.table = String.valueOf(TABLE_ID.incrementAndGet());
    }

    private boolean exec(String... command) throws InterruptedException, IOException {

        Process process =new ProcessBuilder(command).start();
        if (process.waitFor()!=0){
            LineNumberReader reader = new LineNumberReader(new InputStreamReader(process.getErrorStream()));
            String output;
            while ((output=reader.readLine())!=null){
                System.err.println(output);
            }
            return false;
        }
        return true;
    }

    private boolean setupIpaddr(String dev,String ipaddr,int mask) throws IOException, InterruptedException {

        if (!exec("ip","address","add","dev",dev,ipaddr+"/"+mask)){
            throw new RuntimeException("add address to dev %s failed".formatted(dev));
        }
        if (!exec("ip" ,"link", "set", "up", "dev", dev)){
            throw new RuntimeException("setup dev %s failed".formatted(dev));
        }
        return true;
    }

    private boolean setupRoute(String dev,String ipaddr,String table) throws IOException, InterruptedException {

        if (!exec("ip","route","add","default","dev",dev,"table",table)){
            return false;
        }
        if(!exec("ip","route","add",ipaddr+"/32","dev",dev,"src",ipaddr,"table",table)){
            return false;
        }

        if(!exec("ip","rule","add","from",ipaddr+"/32","table",table)){
            return false;
        }
        if(!exec("ip","rule","add","to",ipaddr+"/32","table",table)){
            return false;
        }
        return true;
    }

    private void unloadRule(String ipaddr,String table) throws IOException, InterruptedException {
        exec("ip","rule","del","from",ipaddr+"/32","table",table);
        exec("ip","rule","del","to",ipaddr+"/32","table",table);
    }

    @Override
    public void setup() throws Exception {
        setupIpaddr(dev,ip,mask);
        setupRoute(dev,ip,table);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try {
                unloadRule(ip,table);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }));

    }
}
