package kz.kbtu.objectdetectionlibrary

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import kz.kbtu.objectdetectionlibrary.utils.Utils

class HintActivity : AppCompatActivity() {

    private var layout: LinearLayout? = null
    private var buttonAskPermission: Button? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.f_hint)
        layout = findViewById(R.id.gifImageLayout)
        buttonAskPermission = findViewById(R.id.buttonPermission)
        buttonAskPermission?.setOnClickListener {
            if (!Utils.allPermissionsGranted(this)) {
                Utils.requestRuntimePermissions(this)
            }
            else {
                startActivity(Intent(this, ObjectDetectionActivity::class.java))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Utils.REQUEST_CODE_PHOTO_LIBRARY &&
            resultCode == Activity.RESULT_OK
        ) {
            startActivity(Intent(this, ObjectDetectionActivity::class.java))
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}