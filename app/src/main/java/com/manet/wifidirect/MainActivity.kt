package com.manet.wifidirect

import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

var cxt : Context? = null;
var socket = arrayOf<Socket>(Socket())
var count : Int = 0
var serverSocket = ServerSocket(8888);

class MainActivity : AppCompatActivity() {

    var groupOwnerAddress: String? = null
    private val intentFilter = IntentFilter()
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var manager: WifiP2pManager
    private val handler = Handler()

    var receiver: WiFiDirectBroadcastReceiver? = null
    val LOCATION_REQUEST_CODE  = 0
    var scan : Button? = null
    var create : Button? = null
    var connect : Button? = null
    var send : Button? = null
    var deviceName : TextView? = null
    var textMsg : EditText? = null

    var serverClass : com.manet.wifidirect.MainActivity.ServerClass? = null
    var clientClass : com.manet.wifidirect.MainActivity.ClientClass? = null
    var isHost : Boolean = false
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cxt = this

        scan = findViewById<Button>(R.id.scan) as Button
        create = findViewById<Button>(R.id.cgroup) as Button
        connect = findViewById<Button>(R.id.connect) as Button
        send = findViewById<Button>(R.id.send) as Button
        deviceName = findViewById<TextView>(R.id.device) as TextView
        textMsg = findViewById<EditText>(R.id.editView) as EditText
        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Give Permission.", Toast.LENGTH_SHORT).show()
            checkLocationPermission(this)
        }

        create!!.setOnClickListener(View.OnClickListener {
            connectGroup(this)
        })
        //connectGroup(this)
        scan!!.setOnClickListener(View.OnClickListener {
            discoverPeers()
        })
        connect!!.setOnClickListener(View.OnClickListener {
            connectPeer(receiver!!.peers.get(0))
        })

        send!!.setOnClickListener(View.OnClickListener {
            var msg = textMsg!!.text.toString()
            if(!msg.isNullOrEmpty())
            {
                val executorService = Executors.newSingleThreadExecutor()
                executorService.execute{
                    if(isHost)
                    {
                        serverClass!!.write(msg.toByteArray())

                    }
                    else
                    {
                        if(clientClass == null )
                        {
                            Log.d(TAG,"CLient CLASS is Null boss")
                        }
                        else
                            clientClass!!.write(msg.toByteArray())
                    }

                }
            }
        });

    }

    override fun onDestroy() {
        super.onDestroy()
        disConnect()
    }

    public override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this,handler)
        registerReceiver(receiver, intentFilter)
    }

    public override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    val connectionListener = WifiP2pManager.ConnectionInfoListener { info ->

        // String from WifiP2pInfo struct
        val groupOwnerAddress: InetAddress = info.groupOwnerAddress
        Log.d(TAG,"val groupOwnerAddress: String = "+ groupOwnerAddress.toString())
        Toast.makeText(this,"val groupOwnerAddress: String = "+ groupOwnerAddress.address.toString(),Toast.LENGTH_SHORT).show()
        // After the group negotiation, we can determine the group owner
        // (server).
        if (info.groupFormed && info.isGroupOwner) {
            // Do whatever tasks are specific to the group owner.
            // One common case is creating a group owner thread and accepting
            // incoming connections.
            Toast.makeText(this,"I am Host Now",Toast.LENGTH_SHORT).show()
            Log.d(TAG,"======================THIS DEVICE IS HOST======================")
            isHost = true
            serverClass = ServerClass()
            serverClass!!.start()
        } else if (info.groupFormed) {
            // The other device acts as the peer (client). In this case,
            // you'll want to create a peer thread that connects
            // to the group owner.
            Toast.makeText(this,"I am Client Now",Toast.LENGTH_SHORT).show()
            Log.d(TAG,"======================THIS DEVICE IS Client======================")
            isHost = false
            clientClass = ClientClass(groupOwnerAddress)
            clientClass!!.start()
        }

    }
    fun discoverPeers() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {
                Toast.makeText(cxt,"started scanning",Toast.LENGTH_SHORT).show()
                Log.d(TAG, "discoverPeers : onSuccess() GOT CALLED ")
            }

            override fun onFailure(reasonCode: Int) {
                // Code for when the discovery initiation fails goes here.
                // Alert the user that something went wrong.
                Toast.makeText(cxt,"Scanning failed",Toast.LENGTH_SHORT).show()
                Log.d(TAG, "discoverPeers : onFailure(reasonCode: "+reasonCode+") GOT CALLED ")
            }
        })
    }

    fun connectGroup(context: Context) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                checkLocationPermission(this)
            }
        }

        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Device is ready to accept incoming connections from peers.
                Toast.makeText(
                    context,
                    "Device is ready to accept incoming connections from peers",
                    Toast.LENGTH_SHORT
                ).show()

                Log.d(TAG, "Device is ready to accept incoming connections from peers")
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(context, "P2P group creation failed. Retry."+reason, Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }
    fun connectPeer(local_device: WifiP2pDevice)
    {

        val device: WifiP2pDevice = local_device
        val config = WifiP2pConfig()
        config.deviceAddress = device.deviceAddress
        channel?.also { channel ->
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    //success logic
                    Toast.makeText(applicationContext,"Connected to"+local_device.deviceName,Toast.LENGTH_SHORT).show()
                    Toast.makeText(applicationContext,"Connected to"+local_device.deviceName,Toast.LENGTH_SHORT).show()

                }

                override fun onFailure(reason: Int) {
                    //failure logic
                    Toast.makeText(applicationContext,"failed to connect to"+local_device.deviceName,Toast.LENGTH_SHORT).show()
                }
            }
            )}
    }
    fun disConnect()
    {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                Log.d(TAG, "Disconnect failed. Reason :$reasonCode")
            }

            override fun onSuccess() {
                Log.d(TAG, "Disconnect success. ")
            }
        })
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkLocationPermission(context: Context) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: ")
        when(requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Navigate to the chat fragment
                    Toast.makeText(this, "Got Permission.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    class ServerClass : Thread() {
        private var id = count;
        private var inputStream: InputStream? = null
        private var outputStream: OutputStream? = null

        fun write(bytes: ByteArray?) {
            try {
                outputStream!!.write(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        override fun run() {
            try {
                socket[id] = serverSocket!!.accept()
                inputStream = socket[id]!!.getInputStream()
                outputStream = socket[id]!!.getOutputStream()

            } catch (e: IOException) {
                e.printStackTrace()
            }
            val executorService = Executors.newSingleThreadExecutor()
            val handler = Handler(Looper.getMainLooper())
            executorService.execute {
                val buffer = ByteArray(1024)
                var bytes: Int
                while (socket[id] != null) {
                    try {
                        bytes = inputStream!!.read(buffer)
                        if (bytes > 0) {
                            val finalBytes = bytes
                            handler.post(object : Runnable {
                                override fun run() {
                                    val tempMsg = String(buffer, 0, finalBytes)
                                    Toast.makeText(cxt, "tempMSG : $tempMsg", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            })
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    public class ClientClass(inetAddress: InetAddress) : Thread() {
        var HostAdd: String
        private var inputStream: InputStream? = null
        private var outputStream: OutputStream? = null

        fun write(bytes: ByteArray?) {
            try {
                outputStream!!.write(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        override fun run() {
            try {
                socket[count]!!.connect(InetSocketAddress(HostAdd, 8888), 500)
                inputStream = socket[count]!!.getInputStream()
                outputStream = socket[count]!!.getOutputStream()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val executorService = Executors.newSingleThreadExecutor()
            val handler = Handler(Looper.getMainLooper())
            executorService.execute {
                val buffer = ByteArray(1024)
                var bytes: Int
                while (socket[count] != null) {
                    try {
                        bytes = inputStream!!.read(buffer)
                        if (bytes > 0) {
                            val finalBytes = bytes
                            handler.post(object : Runnable {
                                override fun run() {
                                    val tempMsg = String(buffer, 0, finalBytes)
                                    Toast.makeText(cxt , "tempMSG : $tempMsg", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }

        init {
            HostAdd = inetAddress.hostAddress
            socket[count] = Socket() // EDIT
        }
    }


}