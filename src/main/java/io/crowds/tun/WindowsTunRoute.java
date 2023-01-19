package io.crowds.tun;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

public class WindowsTunRoute implements TunRoute{


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

    @Override
    public void setup() throws Exception {
    }
}
