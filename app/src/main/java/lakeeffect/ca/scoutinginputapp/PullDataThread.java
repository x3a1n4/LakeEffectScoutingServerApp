package lakeeffect.ca.scoutinginputapp;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Created by Ajay on 10/15/2017.
 *
 * Class that deals with pulling the data, this class should not be able to be called multiple times
 */
public class PullDataThread extends Thread{

    boolean running = false;

    BluetoothSocket bluetoothSocket;
    OutputStream out;
    InputStream in;

    MainActivity mainActivity;

    public PullDataThread(BluetoothSocket bluetoothSocket, MainActivity mainActivity){
        this.bluetoothSocket = bluetoothSocket;
        this.mainActivity = mainActivity;
    }

    @Override
    public void run() {
        //send pull request and wait for a response
        try {
            bluetoothSocket.connect();
            in = bluetoothSocket.getInputStream();
            out = bluetoothSocket.getOutputStream();

            if(mainActivity.labels == null){
                out.write("REQUEST LABELS".getBytes(Charset.forName("UTF-8")));
                mainActivity.labels = waitForMessage();
            }

            out.write("REQUEST DATA".getBytes(Charset.forName("UTF-8")));

            String message = waitForMessage();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public String waitForMessage(){
        while(out != null && in != null && bluetoothSocket.isConnected()){
            byte[] bytes = new byte[100000];
            int amount = 0;
            try {
                amount = in.read(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(amount>0)  bytes = Arrays.copyOfRange(bytes, 0, amount);//puts data into bytes and cuts bytes
            else continue;

            return new String(bytes, Charset.forName("UTF-8"));
        }

        return null;
    }

    public void onDestroy() throws IOException {
        if(in!=null) in.close();
        if(out!=null) out.close();
    }
}
