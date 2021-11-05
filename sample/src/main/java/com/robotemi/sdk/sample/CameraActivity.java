//888888888888888888888888888888888888888888888
package com.robotemi.sdk.sample;

import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class CameraActivity extends AppCompatActivity {
    private PreviewView preview;
    private ListenableFuture<ProcessCameraProvider> cameraFuture;

    private InetAddress serverAddr;
    private SocketAddress sc_add;
    private DatagramSocket socket;

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //內部subclass，專門在其他執行續處理socket的傳輸，這樣就不會盯著螢幕矇逼幾小時ㄌ
    private class SocketThread extends AsyncTask<Byte[], Integer, Integer> {

        protected Integer doInBackground(Byte[]... bytearray) {
                try {
                    //把Byte[]轉回byte[]，因為socket傳的是byte[]Orz
                    int j=0;
                    byte[] bytes = new byte[bytearray[0].length];
                    for(Byte b: bytearray[0])
                        bytes[j++] = b.byteValue();

                    DatagramPacket packet = new DatagramPacket(bytes, bytearray[0].length, serverAddr, 1111);
                    socket.send(packet);                             // 傳送
                    //socket.close();                                 // 關閉 UDP socket.

                } catch (Exception e) {
                    Log.e("Socket", "Client: Error", e);
                } finally {
                    //socket.close();
                }
            return 0;
        }
        protected void onProgressUpdate(Integer... progress) { }
        protected void onPostExecute(Integer result) {
            Log.e("Socket", "End...");
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle instanceState)
    {
        super.onCreate(instanceState);

        setContentView(R.layout.activity_main);
        preview = findViewById(R.id.previewView);
        cameraFuture = ProcessCameraProvider.getInstance(this);
        cameraFuture.addListener(
        new Runnable(){
            @Override
            public void run() {
                try{
                    ProcessCameraProvider cameraProvider = cameraFuture.get();
                    bindImageAnalysis(cameraProvider);
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(this));

        try{
            serverAddr = InetAddress.getByName("192.168.50.197");
            sc_add = new InetSocketAddress(serverAddr,1111);
            socket = new DatagramSocket();
        }
        catch (Exception e){Log.e("YO,", "You just fucked up, bro.");}
    }

    private void bindImageAnalysis(@NonNull ProcessCameraProvider provider){
        ImageAnalysis ana=
                new ImageAnalysis.Builder().setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        ana.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                ImageProxy.PlaneProxy planes[] = image.getPlanes();

                ByteBuffer yBuf = planes[0].getBuffer();
                ByteBuffer vuBuf = planes[2].getBuffer();

                int ySize = yBuf.remaining();
                int vuSize = vuBuf.remaining();

                byte nv21[] = new byte[ySize+vuSize];
                yBuf.get(nv21, 0, ySize);
                vuBuf.get(nv21, ySize, vuSize);
                YuvImage img = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                img.compressToJpeg(new Rect(0, 0, img.getWidth(), img.getHeight()), 50, out);

                byte[] mybytearray = out.toByteArray();
                Byte[] byteObjects = new Byte[mybytearray.length];

                //把byte[]包裝成Byte[]，因為SocketThread繼承的AsyncTask不給用byte[]QQ
                int i=0;
                for(byte b: mybytearray)
                    byteObjects[i++] = b;  // Autoboxing.

                //啟動新的執行續，丟入圖片位元陣列~
                new SocketThread().execute(byteObjects);

                image.close();
            }
        });
        Preview prev = new Preview.Builder().build();
        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
        prev.setSurfaceProvider(preview.getSurfaceProvider());

        provider.bindToLifecycle((LifecycleOwner) this, selector, ana, prev);
    }
}