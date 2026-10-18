package com.aether.x.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks app foreground presence using Firebase Realtime Database.
 * Anonymous Firebase Auth gives each installation a stable UID; rules can then
 * restrict writes to /presence/{auth.uid}. RTDB onDisconnect marks a user
 * removed when the socket drops or the process is killed unexpectedly.
 */
class OnlinePresenceRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val presenceRef = database.getReference("presence")
    private val connectedRef = database.getReference(".info/connected")

    private val _onlineCount = MutableStateFlow(-1)
    val onlineCount: StateFlow<Int> = _onlineCount.asStateFlow()

    private val knownOnlineUids = mutableSetOf<String>()
    private var isForeground = false
    private var isConnected = false
    private var signInInProgress = false
    private var connectionListenerAttached = false
    private var presenceListenerAttached = false
    private var closed = false

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            isConnected = snapshot.getValue(Boolean::class.java) == true
            if (isConnected) {
                if (isForeground) publishOnline() else publishOffline()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            isConnected = false
            Log.w(TAG, "Tidak dapat membaca status koneksi Realtime Database: ${error.message}")
        }
    }

    private val presenceListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = updatePresenceCount(snapshot)
        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = updatePresenceCount(snapshot)

        override fun onChildRemoved(snapshot: DataSnapshot) {
            snapshot.key?.let(knownOnlineUids::remove)
            _onlineCount.value = knownOnlineUids.size
        }

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit

        override fun onCancelled(error: DatabaseError) {
            _onlineCount.value = -1
            Log.w(TAG, "Tidak dapat membaca daftar pengguna online: ${error.message}")
        }
    }

    /** Call with true when the app is visible and false when it goes to background. */
    fun setForeground(foreground: Boolean) {
        if (closed) return
        isForeground = foreground
        if (foreground) {
            ensureAnonymousAuth()
        } else {
            publishOffline()
        }
    }

    private fun ensureAnonymousAuth() {
        if (closed) return
        if (auth.currentUser != null) {
            attachRealtimeListeners()
            if (isConnected) publishOnline()
            return
        }
        if (signInInProgress) return
        signInInProgress = true
        auth.signInAnonymously()
            .addOnSuccessListener {
                signInInProgress = false
                if (!closed) {
                    attachRealtimeListeners()
                    if (isConnected && isForeground) publishOnline()
                }
            }
            .addOnFailureListener { error ->
                signInInProgress = false
                _onlineCount.value = -1
                Log.w(TAG, "Autentikasi anonim untuk presence gagal", error)
            }
    }

    private fun attachRealtimeListeners() {
        if (closed || auth.currentUser == null) return
        if (!presenceListenerAttached) {
            knownOnlineUids.clear()
            _onlineCount.value = 0
            presenceRef.addChildEventListener(presenceListener)
            presenceListenerAttached = true
        }
        if (!connectionListenerAttached) {
            connectedRef.addValueEventListener(connectionListener)
            connectionListenerAttached = true
        }
    }

    private fun publishOnline() {
        val uid = auth.currentUser?.uid ?: return
        if (closed || !isForeground || !isConnected) return
        val ownPresence = presenceRef.child(uid)
        // Install the server-side disconnect handler before announcing online.
        ownPresence.onDisconnect()
            .removeValue()
            .addOnSuccessListener {
                if (closed) return@addOnSuccessListener
                if (isForeground && isConnected && auth.currentUser?.uid == uid) {
                    ownPresence.setValue(status("online"))
                        .addOnFailureListener { error ->
                            Log.w(TAG, "Gagal menandai pengguna online", error)
                        }
                } else {
                    ownPresence.removeValue()
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Gagal memasang onDisconnect untuk presence", error)
            }
    }

    private fun publishOffline() {
        val uid = auth.currentUser?.uid ?: return
        if (!isConnected) return // server-side onDisconnect handles a lost connection
        presenceRef.child(uid).removeValue()
            .addOnFailureListener { error ->
                Log.w(TAG, "Gagal menandai pengguna offline", error)
            }
    }

    private fun updatePresenceCount(snapshot: DataSnapshot) {
        val uid = snapshot.key ?: return
        if (snapshot.child("state").getValue(String::class.java) == STATE_ONLINE) {
            knownOnlineUids.add(uid)
        } else {
            knownOnlineUids.remove(uid)
        }
        _onlineCount.value = knownOnlineUids.size
    }

    private fun status(state: String): Map<String, Any> = mapOf(
        "state" to state,
        "lastChanged" to ServerValue.TIMESTAMP,
    )

    /** Remove listeners when the owning screen is disposed. */
    fun close() {
        if (closed) return
        isForeground = false
        publishOffline()
        closed = true
        if (presenceListenerAttached) {
            presenceRef.removeEventListener(presenceListener)
            presenceListenerAttached = false
        }
        if (connectionListenerAttached) {
            connectedRef.removeEventListener(connectionListener)
            connectionListenerAttached = false
        }
    }

    private companion object {
        const val TAG = "AetherXPresence"
        const val STATE_ONLINE = "online"
    }
}
