package mx.cin.adan.speedlogger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.content.Context;
import android.util.Log;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;



public class MainActivity extends AppCompatActivity {

    BluetoothDevice btDevice;
    private ImageButton refreshBtn;
    private Button goBtn;
    private Button stopBtn;
    private static Spinner adapterSpinner;
    private static Spinner alarmTypesSpinner;
    private static TextView textSpeed;
    private static ArrayList<BluetoothDevice> arrayListPairedBluetoothDevices = null;
    private CarConnection loopy;
    private static BluetoothAdapter mBluetoothAdapter;
    private static final String TAG = "SpeedLogger";

    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("adapterSpinner", adapterSpinner.getSelectedItemPosition());
        // do this for each or your Spinner
        // You might consider using Bundle.putStringArray() instead
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        adapterSpinner = (Spinner) findViewById(R.id.adaptersSpinner);
        refreshBtn = (ImageButton) findViewById(R.id.refreshBtn);
        goBtn = (Button) findViewById(R.id.goButton);
        stopBtn = (Button) findViewById(R.id.stopButton);
        arrayListPairedBluetoothDevices = new ArrayList<BluetoothDevice>();
        textSpeed= (TextView) findViewById(R.id.speedIndicator);

        getBtDevices(getApplicationContext());





        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "searching", Toast.LENGTH_LONG).show();
                getBtDevices(getApplicationContext());
            }
        });
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                go();

                goBtn.setVisibility(View.GONE);




            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "Stopping", Toast.LENGTH_LONG).show();
                if(!threadName.equals("")) {
                    loopy.interrupt();
                    threadName="";
                    goBtn.setVisibility(View.VISIBLE);
                }else{
                    Context ctx = MainActivity.this.getApplicationContext();
                    SharedPreferences shPref = ctx.getSharedPreferences("SpeedLogger", MODE_PRIVATE);
                    String threadNameStop = shPref.getString("threadName", "");
                    Thread[] a = new Thread[1000];
                    int n = Thread.enumerate(a);
                    for (int i = 0; i < n; i++) {
                        if (a[i].getName().equals(threadNameStop)) {
                            a[i].interrupt();
                            goBtn.setVisibility(View.VISIBLE);
                            break;
                        }
                    }

                }
            }
        });

        ///finally try go();
        go();

    }//onCreate()
    String threadName ="";

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // process incoming messages here
            Bundle bundle = msg.getData();
            textSpeed.setText(bundle.getString("Test"));
            //Toast.makeText(getApplicationContext(), bundle.getString("Test"), Toast.LENGTH_LONG).show();
        }
    };

    public void go(){

        int pos = adapterSpinner.getSelectedItemPosition();
//                Toast.makeText(getApplicationContext(), "Go ("+pos+")", Toast.LENGTH_LONG).show();
        btDevice = arrayListPairedBluetoothDevices.get(pos);
        mBluetoothAdapter.cancelDiscovery();
        if(!threadName.equals("")){
            try {
                loopy.sleep(500);
                loopy.interrupt();
            }catch(Exception exf){
                Log.e(TAG,"BTNERROR",exf);
            }
        }
        ////////////////

        //////////////


        loopy = new CarConnection(btDevice);
        //loopy = new MyLoopy(mBluetoothAdapter);
        loopy.start();
        threadName=loopy.getName();

        SharedPreferences sharedPref = getSharedPreferences("SpeedLogger",0);
        SharedPreferences.Editor prefEditor = sharedPref.edit();
        prefEditor.putString("adapterSpinnerChoice",adapterSpinner.getSelectedItem().toString());
        prefEditor.putString("threadName",threadName);
        prefEditor.commit();

    }


    public static void getBtDevices(Context ct){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        List<String> s = new ArrayList<String>();
        for(BluetoothDevice bt : pairedDevices) {
            s.add(bt.getName() + "\n" + bt.getAddress());
            arrayListPairedBluetoothDevices.add(bt);
        }

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(ct, R.layout.spinner_custom, s);
        dataAdapter.setDropDownViewResource(R.layout.spinner_dropdown_custom);
        adapterSpinner.setAdapter(dataAdapter);

        SharedPreferences shPref = ct.getSharedPreferences("SpeedLogger", MODE_PRIVATE);
        String compareValue = shPref.getString("adapterSpinnerChoice", "");

        if (!compareValue.equals(null)) {
            int spinnerPosition = dataAdapter.getPosition(compareValue);
            adapterSpinner.setSelection(spinnerPosition);
            // do this for each of your text views
        }

    }



    class CarConnection extends Thread {
        private BluetoothDevice btd=null;
        private BluetoothSocket mmSocket=null;
        private BluetoothServerSocket mmSvSocket;
        private InputStream mmInStream=null;
        private OutputStream mmOutStream=null;
        private BluetoothAdapter btAdapter;
        protected boolean cancel=false;
        private String alarmType="";
        protected int speed=0;

        private int custom=0;

        public final int STATE_NONE = 0;       // we're doing nothing
        public final int STATE_LISTEN = 1;     // now listening for incoming connections
        public final int STATE_CONNECTING = 2; // now initiating an outgoing connection
        public final int STATE_CONNECTED = 3;  // now connected to a remote device

        private UUID MY_UUID_SECURE =
                UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee");
        private UUID MY_UUID_INSECURE =
                UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

        protected ArrayList<Integer> buffer = null;
        /*
         * Defines the code to run for this task.
         */
        CarConnection(BluetoothDevice btdR){
            /**/
            btAdapter = BluetoothAdapter.getDefaultAdapter();
            btd=btAdapter.getRemoteDevice(btdR.getAddress());
            BluetoothSocket tmpSocket=null;

            try {
                sleep(500);
                //mmSocket = btd.createRfcommSocketToServiceRecord( MY_UUID_SECURE);
                //mmSocket = btd.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                final Method m = btd.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
                tmpSocket = (BluetoothSocket) m.invoke(btd, 1);

            } catch (Exception e) {
                Log.e(TAG, "Socket Type: create() failed", e);
            }//catch

            mmSocket = tmpSocket;
        }

        CarConnection(BluetoothAdapter bta){
            try {
                mmSvSocket=bta.listenUsingRfcommWithServiceRecord(TAG,MY_UUID_SECURE);
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: create() failed", e);
            }
            Log.d(TAG, "Server Socket Created "  );
        }

        CarConnection(BluetoothSocket socket){
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { e.printStackTrace();}
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }//CarConnection(BTd)

        boolean isWorking = false;

        public void setCustomLimit(int c){
            custom=c;
        }

        @Override
        public void run() {
            // Moves the current Thread into the background
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            sendDataUi(btd.getName());
            Log.d(TAG, "Trying to connect to: "+btd.getName()+" "+btd.getAddress()  );
            //btAdapter.cancelDiscovery();
            //while (!mmSocket.isConnected())
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                if(!mmSocket.isConnected())mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                Log.e(TAG, "Some Exception at connect()",e);
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                    //  finish();
                }
                //   return;
            }
            Log.d(TAG, ">>Client connectted, Initializing");
            try {
                InputStream mmInStream = mmSocket.getInputStream();
                OutputStream mmOutStream = mmSocket.getOutputStream();

                sendCommandToELM(mmOutStream,"AT D",true);
                sleep(200);
                readResponse(mmInStream, true);
                clearInStream(mmInStream);

                sendCommandToELM(mmOutStream,"AT Z",true);
                sleep(200);
                readResponse(mmInStream, true);
                clearInStream(mmInStream);

                sendCommandToELM(mmOutStream,"AT E0",true);
                sleep(200);
                readResponse(mmInStream, true);
                clearInStream(mmInStream);

                sendCommandToELM(mmOutStream,"AT L0",true);
                sleep(200);
                readResponse(mmInStream, true);
                clearInStream(mmInStream);

                sendCommandToELM(mmOutStream,"AT S0",true);
                sleep(200);
                readResponse(mmInStream, true);
                clearInStream(mmInStream);

                sendCommandToELM(mmOutStream,"AT H0",true);
                sleep(200);
                readResponse(mmInStream, true);
                clearInStream(mmInStream);



                for(int i=0; i<12 && !isWorking;i++) {
                    String lastHex = Integer.toHexString(i);
                    sendCommandToELM(mmOutStream, "AT SP " + lastHex, true);
                    sleep(200);
                    String atsp0Response = readResponse(mmInStream, true);
                    clearInStream(mmInStream);
                    sendDataUi(atsp0Response.trim());
                    if (atsp0Response.toLowerCase().contains("ok"))
                        isWorking = true;
                    else
                        isWorking = false;
                    sleep(200);


                    sendCommandToELM(mmOutStream,"0100",true);
                    sleep(1000);
                    String busInitResponse = readResponse(mmInStream, true);
                    sendDataUi(busInitResponse.trim());
                    clearInStream(mmInStream);
                    clearInStream(mmInStream);

                    if (busInitResponse.toLowerCase().contains("no data") || busInitResponse.toLowerCase().contains("unable") || busInitResponse.toLowerCase().contains("error"))
                        isWorking = false;
                    else
                        isWorking = true;

                    clearInStream(mmInStream);
                    sendCommandToELM(mmOutStream, "010D", true);
                    clearInStream(mmInStream);
                    sleep(200);
                    clearInStream(mmInStream);
                    sendCommandToELM(mmOutStream, "010D", true);
                    clearInStream(mmInStream);
                    sleep(200);
                    clearInStream(mmInStream);
                    sendCommandToELM(mmOutStream, "010D", true);
                    clearInStream(mmInStream);
                    sleep(200);
                    clearInStream(mmInStream);
                    sendCommandToELM(mmOutStream, "010D", true);
                    clearInStream(mmInStream);
                    sleep(5000);

                    String spTest = readResponse(mmInStream,true);
                    Log.d(TAG,"spTest: "+spTest);
                    if (spTest.toLowerCase().contains("no data") || spTest.toLowerCase().contains("unable") || spTest.toLowerCase().contains("error"))
                        isWorking = false;
                    else
                        isWorking = true;
                    sleep(200);

                }
                //////////////////
                int lastSpeed=0;
                while(!cancel){
                    sendCommandToELM(mmOutStream, "010D", false);
                    lastSpeed=speed;
                    speed = readResponseSpeed(mmInStream);
                    if((speed == 0 || speed == 4 ) && lastSpeed > 20 ){
                        //retry 1-p
                        sendCommandToELM(mmOutStream, "010D", false);
                        speed = readResponseSpeed(mmInStream);
                    }
                    if(speed==lastSpeed){

                    }else {
                        sendDataUi(speed + "");
                        sendCloud(speed);
                    }
                    sleep(300);
                }



                Log.i(TAG,"Loop Cancel END");
                ///////////////////


               /* msg =  new Message();
                bundle = new Bundle();
                bundle.putString("Test", speed+"");
                msg.setData(bundle);
                mHandler.sendMessage(msg);*/

            } catch (Exception e) {
                Log.e("AnStELM327", "", e);
            } finally {
                Log.i(TAG,"final");
                if (mmSocket != null) {
                    try {
                        Log.d("AnStELM327", ">>Client Close");
                        mmSocket.close();
                        //finish();
                        return;

                    } catch (IOException e) {
                        Log.e("AnStELM327", "", e);
                    }

                }
            }
            ////////////////////////////////////////////////////



        }//run()



        public void interrupt(){
            cancel = true;
            Log.d(TAG,"Interrumpido");
        }//()

        public void sendCommandToELM(OutputStream mOut,String cmd){
            sendCommandToELM(mOut,cmd,false);
        }//()

        public void sendCommandToELM(OutputStream mOut,String cmd, boolean toDisplay){
            String cmdOut= cmd+"\r\n";
            try {
                mOut.write(cmdOut.getBytes());
                mOut.flush();
                Log.d(TAG, "->" + cmd);
                if(toDisplay) {
                    sendDataUi(cmd);
                }
            }catch(Exception exv){
                Log.d(TAG,"E!cmdOut");
            }

        }//()


        public void sendDataUi(String toSend){
            Message msg =  new Message();
            Bundle bundle = new Bundle();
            ////////////////////////////////////////////////////
            bundle.putString("Test", toSend);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
            Log.d(TAG+"(UI)", toSend);
        }
        public int readResponseSpeed(InputStream in){
            int speed=0;
            try {
                String r = readResponse(in,true);
                String speedRaw = r.trim();
                speedRaw=speedRaw.substring(speedRaw.length()-2,speedRaw.length());
                Log.d(TAG+"<-raw-", speedRaw+"");
                speed = Integer.parseInt(speedRaw.trim(), 16)+4;
            }catch(Exception exx){
                Log.e(TAG, "READ SPEED ERROR",exx);
            }
            Log.d(TAG+"(int)", speed+"");
            sendDataUi(speed+"");

            return speed;
        }
        public String readResponse(InputStream in, boolean log){

            String dataOut="NA NA NA";
            try {
                //byte delimiter =62;//33 = !
                int readBufferPosition = 0;
                int bytesAvailable = in.available();
                if (bytesAvailable > 0) {
                    byte[] packetBytes = new byte[bytesAvailable];
                    byte[] readBuffer = new byte[1024];
                    in.read(packetBytes);
                    for (int i = 0; i < bytesAvailable; i++) {
                        byte b = packetBytes[i];
                        //if (b == delimiter) {
                        if((char)b== 0x3E){
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                            String data = new String(encodedBytes, "US-ASCII");
                            readBufferPosition = 0;
                            dataOut = data;
                            if(log)
                                Log.d(TAG,"<-"+data);
                        } else {
                            readBuffer[readBufferPosition++] = b;
                        }
                    }//for
                }//bytes*/

            }catch (Exception exc){
                Log.e(TAG,"Read Response Error",exc);
            }
            Log.d(TAG, "<-"+dataOut);
            return dataOut;
        }//readResponse

        public void clearInStream(InputStream in){

            try {
                int bytesAvailable = in.available();
                in.skip(bytesAvailable);
                return;
            }catch (Exception exc){
                Log.e(TAG,"Clear Response Error",exc);
            }

        }//readResponse

        public void sendCloud(int sp){

            OutputStream os = null;
            InputStream is = null;
            HttpURLConnection conn = null;
            try {
                //constants
                URL url = new URL("http://158.69.206.28:81/ITS/InterfazServicio.php?q=1");
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("Speed", sp);
                String message = jsonObject.toString();

                conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout( 2000 /*milliseconds*/ );
                conn.setConnectTimeout( 4000 /* milliseconds */ );
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(message.getBytes().length);

                //make some HTTP header nicety
                conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");


                //open
                conn.connect();

                //setup send
                os = new BufferedOutputStream(conn.getOutputStream());
                os.write(message.getBytes());
                //clean up
                os.flush();

                //do somehting with response
                //is = conn.getInputStream();
                //String contentAsString = readIt(is,len);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                //clean up
                try {
                    os.close();
                  //  is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG,"Cloud LastEx Error",e);
                }

                conn.disconnect();
            }
        }


    }//class







}
