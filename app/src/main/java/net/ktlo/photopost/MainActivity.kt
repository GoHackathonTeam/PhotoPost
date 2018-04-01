package net.ktlo.photopost

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private lateinit var resultLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        resultLink = findViewById(R.id.result_link)

        /*
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        */
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val requestTakePhoto = 1
    private val requestEndPublish = 2
    private var tempFilePath : String? = null
    private var fileUri : Uri? = null

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            requestTakePhoto ->
                if (resultCode == Activity.RESULT_OK) {
                    val intent = Intent(this, PostPhotoActivity::class.java)
                    intent.putExtra("filePath", tempFilePath)
                    intent.putExtra("fileUri", fileUri)
                    startActivity(intent)
                    startActivityForResult(intent, requestEndPublish)
                }
            requestEndPublish -> {
                File(fileUri!!.path).delete()
                if (resultCode == Activity.RESULT_OK) {
                    val image = data!!.getSerializableExtra("image")
                    resultLink.text = "<a href=\"$image\">$image</a>"
                }
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        //takePictureIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // Create the File where the photo should go
            val photoFile : File
            try {
                photoFile = File.createTempFile("photo", null, cacheDir)
                //File(cacheDir, ".no_media").createNewFile()
                photoFile.createNewFile()
            } catch (ex : IOException) {
                // Error occurred while creating the File
                return
            }
            val photoURI = FileProvider.getUriForFile(this,
                    BuildConfig.APPLICATION_ID,
                    photoFile)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            startActivityForResult(takePictureIntent, requestTakePhoto)
            tempFilePath = photoFile.absolutePath
            fileUri = photoURI
        }
    }

    fun onClick(view: View) {
        if (view.id == R.id.new_photo_button)
            dispatchTakePictureIntent()
    }
}
