package com.manet.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import android.widget.Toast


var TAG : String = "WiFiDirectBroadcastReceiver"


class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity,
    private val handler: android.os.Handler
) : BroadcastReceiver() {

    public val peers = mutableListOf<WifiP2pDevice>()

    val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val refreshedPeers = peerList.deviceList
        if (!refreshedPeers.equals(peers)) {
            peers.clear()
            peers.addAll(refreshedPeers)

            Log.d(TAG, peers.toString())
            Toast.makeText(activity, "Found a Device"+" List length : "+peers.size, Toast.LENGTH_SHORT).show()
            handler.post {
                activity.deviceName?.text  = peers.get(0).deviceName
                var ind =0;
                for( peer in peers)
                {
                    activity.devices[ind]=peer.deviceName
                    ind++;
                    activity.arrayAdapter!!.notifyDataSetChanged()
                }
            }
        }
        if (peers.isEmpty()) {
            Log.d(TAG, "No devices found")
            Toast.makeText(activity, "No devices found", Toast.LENGTH_SHORT).show()
            return@PeerListListener
        }

    }

    override fun onReceive(context: Context, intent: Intent) {
        val action: String? = intent.action
        when (action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                Log.d(TAG, "WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> ")
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                // Call WifiP2pManager.requestPeers() to get a list of current peers
                Log.d(TAG, "WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> GOT CALLED")
                manager?.requestPeers(channel, peerListListener)
                Log.d(TAG, "P2P peers changed")
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                // Respond to new connection or disconnections
                Log.d(TAG, "WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> ")

                if (manager == null) {
                    Toast.makeText(activity,"MANAGER WAS NULL",Toast.LENGTH_SHORT).show()
                    return
                }

                val networkInfo = intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?

                if (networkInfo!!.isConnected) {

                    // We are connected with the other device, request connection
                    // info to find group owner IP
                    manager.requestConnectionInfo(channel, activity.connectionListener)
                }else{
                    Toast.makeText(activity,"NOT CONNECTED",Toast.LENGTH_SHORT).show()
                    Log.d(TAG,"======================NOT CONNECTED======================")
                }

            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Respond to this device's wifi state changing
                Log.d(TAG, "WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION ->")
            }
        }
    }
}