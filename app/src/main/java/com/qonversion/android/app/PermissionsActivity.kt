package com.qonversion.android.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionPermissionsCallback
import com.qonversion.android.sdk.dto.QPermission
import kotlinx.android.synthetic.main.activity_permissions.*

class PermissionsActivity : AppCompatActivity() {
    private val tag = "PermissionsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        recyclerViewPermissionsList.layoutManager = LinearLayoutManager(this)

        Qonversion.checkPermissions(object: QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                val activePermissions = permissions.values.filter { it.isActive() }
                recyclerViewPermissionsList.adapter = PermissionsAdapter(activePermissions)
            }

            override fun onError(error: QonversionError) {
                Toast.makeText(baseContext, error.description, Toast.LENGTH_LONG).show()
                Log.e(tag, error.toString())
            }
        })
    }
}