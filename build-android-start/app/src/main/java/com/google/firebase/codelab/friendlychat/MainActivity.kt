/**
 * Copyright Google Inc. All Rights Reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.codelab.friendlychat

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import com.bumptech.glide.Glide
import com.crashlytics.android.Crashlytics
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.firebase.ui.database.SnapshotParser
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import de.hdodenhof.circleimageview.CircleImageView

class MainActivity : AppCompatActivity(), OnConnectionFailedListener {

    class MessageViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val messageTextView: TextView = itemView.findViewById<View>(R.id.messageTextView) as TextView
        val messageImageView: ImageView = itemView.findViewById<View>(R.id.messageImageView) as ImageView
        val messengerTextView: TextView = itemView.findViewById<View>(R.id.messengerTextView) as TextView
        val messengerImageView: CircleImageView = itemView.findViewById<View>(R.id.messengerImageView) as CircleImageView
    }

    private var username: String = ANONYMOUS // Set default username is anonymous.
    private var photoUrl: String? = null
    private val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val googleApiClient: GoogleApiClient by lazy {
        GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build()
    }
    private val sendButton: Button by lazy { findViewById<View>(R.id.sendButton) as Button }
    private val messageRecyclerView: RecyclerView by lazy { findViewById<View>(R.id.messageRecyclerView) as RecyclerView }
    private val linearLayoutManager: LinearLayoutManager by lazy { LinearLayoutManager(this) }
    private val progressBar: ProgressBar by lazy { findViewById<View>(R.id.progressBar) as ProgressBar }
    private val messageEditText: EditText by lazy { findViewById<View>(R.id.messageEditText) as EditText }
    private val addMessageImageView: ImageView by lazy { findViewById<View>(R.id.addMessageImageView) as ImageView }

    // Firebase instance variables
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firebaseUser: FirebaseUser? get() = firebaseAuth.currentUser
    private val firebaseDatabaseReference: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference }
    private val firebaseAdapter: FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder> by lazy { createAdapter() }
    private val firebaseAnalytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(this) }
    private val firebaseRemoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        val firebaseUser = this.firebaseUser
        if (firebaseUser == null) { // Not signed in, launch the Sign In activity
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        } else {
            username = firebaseUser.displayName.orEmpty()
            if (firebaseUser.photoUrl != null) {
                photoUrl = firebaseUser.photoUrl.toString()
            }
        }

        linearLayoutManager.stackFromEnd = true
        messageRecyclerView.layoutManager = linearLayoutManager

        firebaseAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val friendlyMessageCount = firebaseAdapter.itemCount
                val lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition()
                // If the recycler view is initially being loaded or the
                // user is at the bottom of the list, scroll to the bottom
                // of the list to show the newly added message.
                if (lastVisiblePosition == -1 ||
                        positionStart >= friendlyMessageCount - 1 &&
                        lastVisiblePosition == positionStart - 1) {
                    messageRecyclerView.scrollToPosition(positionStart)
                }
            }
        })
        messageRecyclerView.adapter = firebaseAdapter

        progressBar.visibility = ProgressBar.INVISIBLE
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                sendButton.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        sendButton.setOnClickListener {
            // Send messages on click.
            val friendlyMessage = FriendlyMessage(messageEditText.text.toString(), username, photoUrl, null)
            firebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(friendlyMessage)
            messageEditText.setText("")
            logMessageSent()
        }
        addMessageImageView.setOnClickListener {
            // Select image for image message on click.
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
            startActivityForResult(intent, REQUEST_IMAGE)
        }

        // Define Firebase Remote Config Settings.
        val firebaseRemoteConfigSettings = FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(true)
                .build()

        // Define default config values. Defaults are used when fetched config values are not
        // available. Eg: if an error occurred fetching values from the server.
        val defaultConfigMap = mapOf("friendly_msg_length" to 10L)

        // Apply config settings and default values.
        firebaseRemoteConfig.setConfigSettings(firebaseRemoteConfigSettings)
        firebaseRemoteConfig.setDefaults(defaultConfigMap)

        // Fetch remote config.
        fetchConfig()
    }

    // Fetch the config to determine the allowed length of messages.
    private fun fetchConfig() {
        val cacheExpiration = if (firebaseRemoteConfig.info.configSettings.isDeveloperModeEnabled) {
            // If developer mode is enabled reduce cacheExpiration to 0 so that
            // each fetch goes to the server. This should not be used in release builds.
            0
        } else {
            3600L // 1 hour in seconds
        }
        firebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener {
                    // Make the fetched config available via FirebaseRemoteConfig get<type> calls.
                    firebaseRemoteConfig.activateFetched()
                    applyRetrievedLengthLimit()
                }
                .addOnFailureListener { e ->
                    // There has been an error fetching the config
                    Log.w(TAG, "Error fetching config: " + e.message)
                    applyRetrievedLengthLimit()
                }
        // print app's Instance ID token.
        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Remote instance ID token: " + task.result!!.token)
            }
        }
    }


    /**
     * Apply retrieved length limit to edit text field.
     * This result may be fresh from the server or it may be from cached
     * values.
     */
    private fun applyRetrievedLengthLimit() {
        val friendly_msg_length = firebaseRemoteConfig.getLong("friendly_msg_length").toInt()
        messageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(friendly_msg_length))
        Log.d(TAG, "FML is: $friendly_msg_length")
    }

    private fun createAdapter(): FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder> {
        val parser: SnapshotParser<FriendlyMessage> = SnapshotParser { dataSnapshot ->
            dataSnapshot.getValue(FriendlyMessage::class.java)!!.apply { this.id = dataSnapshot.key }
        }
        val messagesRef = firebaseDatabaseReference.child(MESSAGES_CHILD)
        val options = FirebaseRecyclerOptions.Builder<FriendlyMessage>()
                .setQuery(messagesRef, parser)
                .build()

        return object : FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>(options) {
            override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): MessageViewHolder {
                val inflater = LayoutInflater.from(viewGroup.context)
                return MessageViewHolder(inflater.inflate(R.layout.item_message, viewGroup, false))
            }

            override fun onBindViewHolder(viewHolder: MessageViewHolder,
                                          position: Int,
                                          friendlyMessage: FriendlyMessage) {
                progressBar.visibility = ProgressBar.INVISIBLE
                if (friendlyMessage.text != null) {
                    viewHolder.messageTextView.text = friendlyMessage.text
                    viewHolder.messageTextView.visibility = TextView.VISIBLE
                    viewHolder.messageImageView.visibility = ImageView.GONE
                } else if (friendlyMessage.imageUrl != null) {
                    val imageUrl = friendlyMessage.imageUrl!!
                    if (imageUrl.startsWith("gs://")) {
                        val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
                        storageReference.downloadUrl.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val downloadUrl = task.result.toString()
                                Glide.with(viewHolder.messageImageView.context)
                                        .load(downloadUrl)
                                        .into(viewHolder.messageImageView)
                            } else {
                                Log.w(TAG, "Getting download url was not successful.", task.exception)
                            }
                        }
                    } else {
                        Glide.with(viewHolder.messageImageView.context)
                                .load(friendlyMessage.imageUrl)
                                .into(viewHolder.messageImageView)
                    }
                    viewHolder.messageImageView.visibility = ImageView.VISIBLE
                    viewHolder.messageTextView.visibility = TextView.GONE
                }
                viewHolder.messengerTextView.text = friendlyMessage.name
                if (friendlyMessage.photoUrl == null) {
                    val drawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_account_circle_black_36dp)
                    viewHolder.messengerImageView.setImageDrawable(drawable)
                } else {
                    Glide.with(this@MainActivity)
                            .load(friendlyMessage.photoUrl)
                            .into(viewHolder.messengerImageView)
                }
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in.
        // TODO: Add code to check if user is signed in.
    }

    public override fun onPause() {
        firebaseAdapter.stopListening()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        firebaseAdapter.startListening()
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.sign_out_menu -> {
            firebaseAuth.signOut()
            Auth.GoogleSignInApi.signOut(googleApiClient)
            username = ANONYMOUS
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            true
        }
        R.id.crash_menu -> {
            causeCrash()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun causeCrash() {
        Crashlytics.getInstance().crash();
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not be available.
        Log.d(TAG, "onConnectionFailed:$connectionResult")
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == REQUEST_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data
            Log.d(TAG, "Uri: $uri")
            val tempMessage = FriendlyMessage(null, username, photoUrl, LOADING_IMAGE_URL)
            firebaseDatabaseReference.child(MESSAGES_CHILD).push()
                    .setValue(tempMessage) { databaseError, databaseReference ->
                        if (databaseError == null) {
                            val key = databaseReference.key
                            val storageReference = FirebaseStorage.getInstance()
                                    .getReference(firebaseUser!!.uid)
                                    .child(key!!)
                                    .child(uri.lastPathSegment)
                            putImageInStorage(storageReference, uri, key)
                        } else {
                            Log.w(TAG, "Unable to write message to database.", databaseError.toException())
                        }
                    }
        }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri, key: String?) {
        storageReference.putFile(uri).addOnCompleteListener(this@MainActivity) { task1 ->
            if (task1.isSuccessful) {
                task1.result!!.metadata!!.reference!!.downloadUrl
                        .addOnCompleteListener(this@MainActivity) { task2 ->
                            if (task2.isSuccessful) {
                                val friendlyMessage = FriendlyMessage(null, username, photoUrl, task2.result.toString())
                                firebaseDatabaseReference
                                        .child(MESSAGES_CHILD)
                                        .child(key!!)
                                        .setValue(friendlyMessage)
                            }
                        }
            } else {
                Log.w(TAG, "Image upload task was not successful.", task1.exception)
            }
        }
    }

    private fun logMessageSent() {
        // Log message has been sent.
        firebaseAnalytics.logEvent("message", null)
    }

    companion object {
        private const val TAG = "MainActivity"
        const val MESSAGES_CHILD = "messages"
        private const val REQUEST_INVITE = 1
        private const val REQUEST_IMAGE = 2
        private const val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"
        const val DEFAULT_MSG_LENGTH_LIMIT = 10
        const val ANONYMOUS = "anonymous"
        private const val MESSAGE_SENT_EVENT = "message_sent"
        private const val MESSAGE_URL = "http://friendlychat.firebase.google.com/message/"
    }
}