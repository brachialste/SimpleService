/*
 * Copyright AllSeen Alliance. All rights reserved.
 *
 *    Permission to use, copy, modify, and/or distribute this software for any
 *    purpose with or without fee is hereby granted, provided that the above
 *    copyright notice and this permission notice appear in all copies.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package org.alljoyn.bus.samples.simpleservice;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.samples.simpleservice.bstp.BSTProtocolCommands;
import org.alljoyn.bus.samples.simpleservice.bstp.BSTProtocolMessage;
import org.alljoyn.bus.samples.simpleservice.security.DiffieHellmanManager;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;


public class ServiceActivity extends Activity {
    /* Load the native alljoyn_java library. */
    static {
        System.loadLibrary("alljoyn_java");
    }

    private static final String TAG = "SimpleService";

    private static final int MESSAGE_PING = 1;
    private static final int MESSAGE_PING_REPLY = 2;
    private static final int MESSAGE_POST_TOAST = 3;
    private static final int MESSAGE_CONN = 4;
    private static final int MESSAGE_RQST = 5;
    private static final int MESSAGE_CLSE = 6;

    private ArrayAdapter<String> mListViewArrayAdapter;
    private ListView mListView;
    private Menu menu;
    private Button start;

    public static final String SSID = "iRED ES00000_00"; // TODO: Setear el nombre de la red con el nombre de la estación

    // seguridad
    private DiffieHellmanManager diffieHellmanManager;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_PING:
                    String ping = (String) msg.obj;
                    mListViewArrayAdapter.add("Ping:  " + ping);
                    break;
                case MESSAGE_PING_REPLY:
                    String reply = (String) msg.obj;
                    mListViewArrayAdapter.add("Reply:  " + reply);
                    break;
                case MESSAGE_POST_TOAST:
                    Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_LONG).show();
                    break;
                case MESSAGE_CONN:
                    String bstp_message = new String((byte[])msg.obj);
                    mListViewArrayAdapter.add("CONN:  " + bstp_message);
                    break;
                case MESSAGE_RQST:
                    String rqst_message = new String((byte[])msg.obj);
                    mListViewArrayAdapter.add("RQST:  " + rqst_message);
                    break;
                case MESSAGE_CLSE:
                    String clse_message = new String((byte[])msg.obj);
                    mListViewArrayAdapter.add("CLSE:  " + clse_message);
                    break;
                default:
                    break;
            }
        }
    };

    /* The AllJoyn object that is our service. */
    private SimpleService mSimpleService;

    /* Handler used to make calls to AllJoyn methods. See onCreate(). */
    private Handler mBusHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mListViewArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mListView = (ListView) findViewById(R.id.ListView);
        mListView.setAdapter(mListViewArrayAdapter);

        start = (Button) findViewById(R.id.start);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // En caso de que sea posible el Hotspot
                if(ApManager.configApState(ServiceActivity.this, SSID)) {

                    /* Make all AllJoyn calls through a separate handler thread to prevent blocking the UI. */
                    HandlerThread busThread = new HandlerThread("BusHandler");
                    busThread.start();
                    mBusHandler = new BusHandler(busThread.getLooper());

                    /* Start our service. */
                    mSimpleService = new SimpleService();
                    mBusHandler.sendEmptyMessage(BusHandler.CONNECT);
                }

                // ocultamos el boton
                start.setVisibility(View.GONE);
            }
        });


    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.quit:
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        /* Disconnect to prevent any resource leaks. */
        mBusHandler.sendEmptyMessage(BusHandler.DISCONNECT);
    }

    /* The class that is our AllJoyn service.  It implements the SimpleInterface. */
    class SimpleService implements SimpleInterface, BusObject {

        /*
         * This is the code run when the client makes a call to the Ping method of the
         * SimpleInterface.  This implementation just returns the received String to the caller.
         *
         * This code also prints the string it received from the user and the string it is
         * returning to the user to the screen.
         */
        public String Ping(String inStr) {
            sendUiMessage(MESSAGE_PING, inStr);

            /* Simply echo the ping message. */
            sendUiMessage(MESSAGE_PING_REPLY, inStr);
            return inStr;
        }

        @Override
        public byte[] CONN(byte[] inBytes) throws BusException {
            logInfo("CONN");
            // enviamos a la interfase el mensaje recibido
            sendUiMessage(MESSAGE_CONN, inBytes);

            BSTProtocolMessage bstProtocolMessage = new BSTProtocolMessage(inBytes);
            // leemos el mensaje recibido
            if (bstProtocolMessage.getCmd() != null && bstProtocolMessage.getCmd().equals(BSTProtocolCommands.connect)) {
                logInfo("connect. ");
                /** CONN **/
                try {
                    // inicializamos el canal seguro usando BSTP
                    diffieHellmanManager = DiffieHellmanManager.createNewInstance();
                    // genramos la llave pública
                    String pk = diffieHellmanManager.generatePublicKey();
                    JSONObject data = new JSONObject();
                    try {
                        data.put("pk", pk);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    // generamos la llave privada
                    if (diffieHellmanManager.generateSecretKey(bstProtocolMessage.getData().getString("pk"))) {
                        // hacemos vibrar el dispositivo
                        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        // Vibrar por 500 mils
                        v.vibrate(500);

                        // reproducimos un sonido para la conexión Bluetooth
                        MediaPlayer mp = MediaPlayer.create(ServiceActivity.this, R.raw.tone);
                        mp.start();

                        // enviamos un mensaje a al interfase
                        sendUiMessage(MESSAGE_POST_TOAST, "Se ha iniciado sesión segura con un dispositivo");

                        Log.i(TAG, "Se ha iniciado sesión segura con un dispositivo");
                    }

                    // enviamos el mensaje de respuesta
                    BSTProtocolMessage responseMessage = new BSTProtocolMessage(BSTProtocolCommands.connect, data);
                    return responseMessage.getTramaByte();

                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        @Override
        public byte[] RQST(byte[] inBytes) throws BusException {
            logInfo("RQST");
            // enviamos a la interfase el mensaje recibido
            sendUiMessage(MESSAGE_RQST, inBytes);

            BSTProtocolMessage bstProtocolMessage = new BSTProtocolMessage(inBytes);
            // leemos el mensaje recibido
            if (bstProtocolMessage.getCmd() != null && bstProtocolMessage.getCmd().equals(BSTProtocolCommands.request)) {
                logInfo("request. ");
                /** RQST **/
                try {

                    // enviamos el mensaje de respuesta
                    BSTProtocolMessage responseMessage = new BSTProtocolMessage(BSTProtocolCommands.accept, bstProtocolMessage.getData());
                    return responseMessage.getTramaByte();

//                } catch (JSONException e) {
//                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        @Override
        public byte[] CLSE(byte[] inBytes) throws BusException {
            logInfo("CLSE");
            // enviamos a la interfase el mensaje recibido
            sendUiMessage(MESSAGE_CLSE, inBytes);

            BSTProtocolMessage bstProtocolMessage = new BSTProtocolMessage(inBytes);
            // leemos el mensaje recibido
            if (bstProtocolMessage.getCmd() != null && bstProtocolMessage.getCmd().equals(BSTProtocolCommands.close)) {
                logInfo("close. ");

                // terminamos la sesión
                mBusHandler.sendEmptyMessage(BusHandler.DISCONNECT);

                /** CLSE **/
                try {

                    // enviamos el mensaje de respuesta
                    BSTProtocolMessage responseMessage = new BSTProtocolMessage(BSTProtocolCommands.close, new JSONObject());
                    return responseMessage.getTramaByte();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return null;
        }


        /* Helper function to send a message to the UI thread. */
        private void sendUiMessage(int what, Object obj) {
            mHandler.sendMessage(mHandler.obtainMessage(what, obj));
        }
    }

    /* This class will handle all AllJoyn calls. See onCreate(). */
    class BusHandler extends Handler {
        /*
         * Name used as the well-known name and the advertised name.  This name must be a unique name
         * both to the bus and to the network as a whole.  The name uses reverse URL style of naming.
         */
        private static final String SERVICE_NAME = "mx.ired.Bus.estacion";
        private static final short CONTACT_PORT = 42;

        private BusAttachment mBus;

        /* These are the messages sent to the BusHandler from the UI. */
        public static final int CONNECT = 1;
        public static final int DISCONNECT = 2;

        public BusHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            /* Connect to the bus and start our service. */
            case CONNECT: {
                logInfo("CONNECT");
                org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
                /*
                 * All communication through AllJoyn begins with a BusAttachment.
                 *
                 * A BusAttachment needs a name. The actual name is unimportant except for internal
                 * security. As a default we use the class name as the name.
                 *
                 * By default AllJoyn does not allow communication between devices (i.e. bus to bus
                 * communication).  The second argument must be set to Receive to allow
                 * communication between devices.
                 */
                mBus = new BusAttachment(getPackageName(), BusAttachment.RemoteMessage.Receive);

                /*
                 * Create a bus listener class
                 */
                mBus.registerBusListener(new BusListener());

                /*
                 * To make a service available to other AllJoyn peers, first register a BusObject with
                 * the BusAttachment at a specific path.
                 *
                 * Our service is the SimpleService BusObject at the "/SimpleService" path.
                 */
                Status status = mBus.registerBusObject(mSimpleService, "/ired");
                logStatus("BusAttachment.registerBusObject()", status);
                if (status != Status.OK) {
                    finish();
                    return;
                }



                /*
                 * The next step in making a service available to other AllJoyn peers is to connect the
                 * BusAttachment to the bus with a well-known name.
                 */
                /*
                 * connect the BusAttachement to the bus
                 */
                status = mBus.connect();
                logStatus("BusAttachment.connect()", status);
                if (status != Status.OK) {
                    finish();
                    return;
                }

                /*
                 * Create a new session listening on the contact port of the chat service.
                 */
                Mutable.ShortValue contactPort = new Mutable.ShortValue(CONTACT_PORT);

                SessionOpts sessionOpts = new SessionOpts();
                sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
                sessionOpts.isMultipoint = false;
                sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
                sessionOpts.transports = SessionOpts.TRANSPORT_ANY;

                status = mBus.bindSessionPort(contactPort, sessionOpts, new SessionPortListener() {
                    @Override
                    public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
                        if (sessionPort == CONTACT_PORT) {
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
                logStatus(String.format("BusAttachment.bindSessionPort(%d, %s)",
                                        contactPort.value, sessionOpts.toString()), status);
                if (status != Status.OK) {
                    finish();
                    return;
                }

                /*
                 * request a well-known name from the bus
                 */
                int flag = BusAttachment.ALLJOYN_REQUESTNAME_FLAG_REPLACE_EXISTING | BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;

                status = mBus.requestName(SERVICE_NAME, flag);
                logStatus(String.format("BusAttachment.requestName(%s, 0x%08x)", SERVICE_NAME, flag), status);
                if (status == Status.OK) {
                    /*
                     * If we successfully obtain a well-known name from the bus
                     * advertise the same well-known name
                     */
                    status = mBus.advertiseName(SERVICE_NAME, sessionOpts.transports);
                    logStatus(String.format("BusAttachement.advertiseName(%s)", SERVICE_NAME), status);
                    if (status != Status.OK) {
                        /*
                         * If we are unable to advertise the name, release
                         * the well-known name from the local bus.
                         */
                        status = mBus.releaseName(SERVICE_NAME);
                        logStatus(String.format("BusAttachment.releaseName(%s)", SERVICE_NAME), status);
                        finish();
                        return;
                    }
                }

                break;
            }

            /* Release all resources acquired in connect. */
            case DISCONNECT: {
                logInfo("DISCONNECT");
                // apagamos el SSID
                ApManager.configApState(ServiceActivity.this, SSID);

                // esperamos 1 segundo
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                /*
                 * It is important to unregister the BusObject before disconnecting from the bus.
                 * Failing to do so could result in a resource leak.
                 */
                mBus.unregisterBusObject(mSimpleService);
                mBus.disconnect();
                mBusHandler.getLooper().quit();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // limpiamos la vista
                        mListViewArrayAdapter.clear();
                        mListViewArrayAdapter.notifyDataSetChanged();
                        // mostramos el boton
                        start.setVisibility(View.VISIBLE);
                    }
                });

                break;
            }

            default:
                break;
            }
        }
    }

    private void logStatus(String msg, Status status) {
        String log = String.format("%s: %s", msg, status);
        if (status == Status.OK) {
            Log.i(TAG, log);
        } else {
            Message toastMsg = mHandler.obtainMessage(MESSAGE_POST_TOAST, log);
            mHandler.sendMessage(toastMsg);
            Log.e(TAG, log);
        }
    }

    /*
     * print the status or result to the Android log. If the result is the expected
     * result only print it to the log.  Otherwise print it to the error log and
     * Sent a Toast to the users screen.
     */
    private void logInfo(String msg) {
        Log.i(TAG, msg);
    }
}
