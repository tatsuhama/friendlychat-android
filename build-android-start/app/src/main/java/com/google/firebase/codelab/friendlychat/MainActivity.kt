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

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import de.hdodenhof.circleimageview.CircleImageView

class MainActivity : AppCompatActivity(), OnConnectionFailedListener {

    class MessageViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val messageTextView: TextView = itemView.findViewById<View>(R.id.messageTextView) as TextView
        val messageImageView: ImageView = itemView.findViewById<View>(R.id.messageImageView) as ImageView
        val messengerTextView: TextView = itemView.findViewById<View>(R.id.messengerTextView) as TextView
        val messengerImageView: CircleImageView = itemView.findViewById<View>(R.id.messengerImageView) as CircleImageView
    }

    private var username: String = ANONYMOUS // Set default username is anonymous.
    private val photoUrl: String? = null
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        linearLayoutManager.stackFromEnd = true
        messageRecyclerView.layoutManager = linearLayoutManager
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
        }
        addMessageImageView.setOnClickListener {
            // Select image for image message on click.
        }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in.
        // TODO: Add code to check if user is signed in.
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
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
        return super.onOptionsItemSelected(item)
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not be available.
        Log.d(TAG, "onConnectionFailed:$connectionResult")
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show()
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