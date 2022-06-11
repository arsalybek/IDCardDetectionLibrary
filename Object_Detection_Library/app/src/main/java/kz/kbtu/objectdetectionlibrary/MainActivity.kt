package kz.kbtu.objectdetectionlibrary

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kz.kbtu.objectdetectionlibrary.utils.Utils

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startActivity(Intent(this, HintActivity::class.java))
    }

//    override fun onResume() {
//        super.onResume()
//        if (!Utils.allPermissionsGranted(this)) {
//            Utils.requestRuntimePermissions(this)
//        }
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        if (requestCode == Utils.REQUEST_CODE_PHOTO_LIBRARY &&
//            resultCode == Activity.RESULT_OK &&
//            data != null
//        ) {
//            val intent = Intent(this, ObjectDetectionActivity::class.java)
//            intent.data = data.data
//            startActivity(intent)
//        } else {
//            super.onActivityResult(requestCode, resultCode, data)
//        }
//    }
}