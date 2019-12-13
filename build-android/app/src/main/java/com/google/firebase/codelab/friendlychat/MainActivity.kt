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
import android.support.v7.widget.RecyclerView.AdapterDataObserver
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.firebase.ui.database.SnapshotParser
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
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

    private var mUsername: String? = null
    private var mPhotoUrl: String? = null
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

    private fun getFirebaseUser(): FirebaseUser? = firebaseAuth.currentUser
    private val firebaseDatabaseReference: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference }
    private val firebaseAdapter: FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder> by lazy { createAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Set default username is anonymous.
        mUsername = ANONYMOUS
        // Initialize Firebase Auth
        val firebaseUser = getFirebaseUser()
        if (firebaseUser == null) { // Not signed in, launch the Sign In activity
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        } else {
            mUsername = firebaseUser.displayName
            if (firebaseUser.photoUrl != null) {
                mPhotoUrl = firebaseUser.photoUrl.toString()
            }
        }
        // Initialize ProgressBar and RecyclerView.
        linearLayoutManager.stackFromEnd = true
        messageRecyclerView.layoutManager = linearLayoutManager
        firebaseAdapter.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val friendlyMessageCount = firebaseAdapter.getItemCount()
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
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                if (charSequence.toString().trim { it <= ' ' }.length > 0) {
                    sendButton.isEnabled = true
                } else {
                    sendButton.isEnabled = false
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        sendButton.setOnClickListener {
            val friendlyMessage = FriendlyMessage(messageEditText.text.toString(),
                    mUsername,
                    mPhotoUrl,
                    null /* no image */)
            firebaseDatabaseReference.child(MESSAGES_CHILD)
                    .push().setValue(friendlyMessage)
            messageEditText.setText("")
        }
        addMessageImageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }
    }

    private fun createAdapter(): FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder> {
        val parser: SnapshotParser<FriendlyMessage> = SnapshotParser<FriendlyMessage> { dataSnapshot ->
            val friendlyMessage = dataSnapshot.getValue(FriendlyMessage::class.java)!!
            if (friendlyMessage != null) {
                friendlyMessage.id = dataSnapshot.key
            }
            friendlyMessage
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                firebaseAuth.signOut()
                Auth.GoogleSignInApi.signOut(googleApiClient)
                mUsername = ANONYMOUS
                startActivity(Intent(this, SignInActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not be available.
        Log.d(TAG, "onConnectionFailed:$connectionResult")
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    val uri = data.data
                    Log.d(TAG, "Uri: $uri")
                    val tempMessage = FriendlyMessage(null, mUsername, mPhotoUrl, LOADING_IMAGE_URL)
                    firebaseDatabaseReference.child(MESSAGES_CHILD).push()
                            .setValue(tempMessage) { databaseError, databaseReference ->
                                if (databaseError == null) {
                                    val key = databaseReference.key
                                    val storageReference = FirebaseStorage.getInstance()
                                            .getReference(getFirebaseUser()!!.uid)
                                            .child(key!!)
                                            .child(uri.lastPathSegment)
                                    putImageInStorage(storageReference, uri, key)
                                } else {
                                    Log.w(TAG, "Unable to write message to database.", databaseError.toException())
                                }
                            }
                }
            }
        }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri, key: String?) {
        storageReference.putFile(uri).addOnCompleteListener(this@MainActivity) { task ->
            if (task.isSuccessful) {
                task.result!!.metadata!!.reference!!.downloadUrl
                        .addOnCompleteListener(this@MainActivity) { task ->
                            if (task.isSuccessful) {
                                val friendlyMessage = FriendlyMessage(null, mUsername, mPhotoUrl, task.result.toString())
                                firebaseDatabaseReference
                                        .child(MESSAGES_CHILD)
                                        .child(key!!)
                                        .setValue(friendlyMessage)
                            }
                        }
            } else {
                Log.w(TAG, "Image upload task was not successful.",
                        task.exception)
            }
        }
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