package com.example.prefixblocker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val roleRequestCode = 1001
    private val permissionRequestCode = 1002

    private lateinit var statusText: TextView
    private lateinit var enableSwitch: Switch
    private lateinit var roleButton: Button
    private lateinit var prefixInput: EditText
    private lateinit var addButton: Button
    private lateinit var prefixContainer: LinearLayout
    private lateinit var testInput: EditText
    private lateinit var testButton: Button
    private lateinit var testResult: TextView
    private lateinit var logText: TextView
    private lateinit var clearLogButton: Button

    private val phonePermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        enableSwitch = findViewById(R.id.enableSwitch)
        roleButton = findViewById(R.id.roleButton)
        prefixInput = findViewById(R.id.prefixInput)
        addButton = findViewById(R.id.addButton)
        prefixContainer = findViewById(R.id.prefixContainer)
        testInput = findViewById(R.id.testInput)
        testButton = findViewById(R.id.testButton)
        testResult = findViewById(R.id.testResult)
        logText = findViewById(R.id.logText)
        clearLogButton = findViewById(R.id.clearLogButton)

        enableSwitch.isChecked = PrefixStore.isEnabled(this)
        enableSwitch.setOnCheckedChangeListener { _, checked ->
            PrefixStore.setEnabled(this, checked)
            refreshStatus()
        }

        roleButton.setOnClickListener { requestScreeningRole() }

        addButton.setOnClickListener {
            val raw = prefixInput.text.toString()
            if (PrefixStore.addPrefix(this, raw)) {
                prefixInput.text.clear()
                refreshPrefixes()
                refreshStatus()
            } else {
                Toast.makeText(this, "Enter digits, e.g. 140 or 080 4602 XXXX", Toast.LENGTH_SHORT).show()
            }
        }

        testButton.setOnClickListener {
            val number = testInput.text.toString()
            val match = PhoneNormalize.findMatchingPrefix(number, PrefixStore.getPrefixes(this))
            testResult.text = if (match != null) {
                "Would BLOCK \"$number\" (matched $match)."
            } else {
                "Would ALLOW \"$number\"."
            }
        }

        clearLogButton.setOnClickListener {
            BlockLogStore.clear(this)
            refreshLog()
        }

        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshPrefixes()
        refreshLog()
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val missing = phonePermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), permissionRequestCode)
        }
    }

    private fun isScreeningRoleHeld(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null // no RoleManager role pre-10
        val rm = getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AlertDialog.Builder(this)
                .setTitle("Android 9 and below")
                .setMessage(
                    "Android 10+ lets any app become the Call Screening app. " +
                        "On older versions the screening service only runs reliably " +
                        "if this app is set as the default Phone app. " +
                        "This build targets modern Android — for best results use Android 10+."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val rm = getSystemService(RoleManager::class.java)
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Call screening role not available on this device.", Toast.LENGTH_LONG).show()
            return
        }
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Already set as Call Screening app.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(
            rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
            roleRequestCode
        )
    }

    @Deprecated("Legacy role request callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == roleRequestCode) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Call screening enabled.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screening NOT enabled — calls won't be blocked until you grant it.", Toast.LENGTH_LONG).show()
            }
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val enabled = PrefixStore.isEnabled(this)
        val count = PrefixStore.getPrefixes(this).size
        val role = isScreeningRoleHeld()
        val roleLine = when (role) {
            true -> "Call screening: ON (this app will auto-decline matches)."
            false -> "Call screening: OFF — tap the button above to enable, otherwise nothing is blocked."
            null -> "Android 9 or older: screening needs default-Phone status; use Android 10+ for one-tap setup."
        }
        statusText.text = "Blocking: ${if (enabled) "ON" else "OFF"} ($count prefixes)\n$roleLine"
    }

    private fun refreshPrefixes() {
        prefixContainer.removeAllViews()
        val prefixes = PrefixStore.getPrefixes(this).sorted()
        if (prefixes.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No prefixes yet — add one above."
            prefixContainer.addView(tv)
            return
        }
        for (prefix in prefixes) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val label = TextView(this)
            label.text = prefix
            label.textSize = 18f
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            label.layoutParams = lp
            val del = Button(this)
            del.text = "✕"
            del.setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage("Stop blocking numbers starting with $prefix?")
                    .setPositiveButton("Remove") { _, _ ->
                        PrefixStore.removePrefix(this, prefix)
                        refreshPrefixes()
                        refreshStatus()
                    }
                    .setNegativeButton("Keep", null)
                    .show()
            }
            row.addView(label)
            row.addView(del)
            prefixContainer.addView(row)
        }
    }

    private fun refreshLog() {
        val entries = BlockLogStore.entries(this)
        logText.text = if (entries.isEmpty()) {
            "Nothing blocked yet."
        } else {
            entries.joinToString("\n")
        }
    }
}
