package net.ktlo.photopost

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import android.util.JsonReader
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import kotlinx.android.synthetic.main.activity_post_photo.*
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread


class PostPhotoActivity : AppCompatActivity() {

    private val tag = "PhotoPost"

    private lateinit var imageUri: Uri
    private lateinit var takenPhoto: ImageView
    private lateinit var nameField: EditText
    private lateinit var descriptionField: EditText
    private lateinit var publishButton: Button
    private lateinit var progressSpin: ProgressBar
    private lateinit var clientHeader: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_photo)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        takenPhoto = findViewById(R.id.taken_photo)
        nameField = findViewById(R.id.photo_name)
        descriptionField = findViewById(R.id.description)
        publishButton = findViewById(R.id.publish_button)
        progressSpin = findViewById(R.id.progress)
        imageUri =  intent.extras["fileUri"] as Uri
        takenPhoto.setImageURI(imageUri)
        clientHeader = "Client-ID " + getString(R.string.client_id)
    }

    private fun anotherPostPhoto() {
        val imageStream = contentResolver.openInputStream(imageUri)
        val connection = URL("https://api.imgur.com/3/image").openConnection() as HttpURLConnection
        var response: ImgurUploadResponse? = null
        lateinit var errorResponse: String

        fun pipe(input: InputStream, output: OutputStream) {
            val buffer = ByteArray(100)
            var red = input.read(buffer)
            while (red > 0) {
                output.write(buffer, 0, red)
                red = input.read(buffer)
            }
        }

        try {
            connection.setRequestProperty("Authorization", clientHeader)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setChunkedStreamingMode(0)

            val output = connection.outputStream

            pipe(imageStream, output)
            output.close()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                response = parseResponse(JsonReader(connection.inputStream.reader()))
                //val response = connection.inputStream.reader().readText()
                Log.d(tag, response.toString())
            } else {
                errorResponse = connection.errorStream.reader().readText()
                Log.d(tag, errorResponse)
            }
        } finally {
            connection.disconnect()
            imageStream.close()
        }

        if (response == null) {
            showErrorDialog(errorResponse)
            return
        }

        if (nameField.text.isNotBlank() || descriptionField.text.isNotBlank())
            if (!updateImageInfo(response.data))
                return

        //runOnUiThread {
            finish(response.data)
        //}
    }

    private fun showErrorDialog(errorResponse: String) {
        AlertDialog.Builder(this).apply {
            setMessage(errorResponse)
            title = getString(R.string.error_dialog)
            setPositiveButton(R.string.ok) {
                _, _ ->
                runOnUiThread { exitBad() }
            }
        }
    }

    private fun finish(response: ImageData) {
        val intent = Intent().apply {
            putExtra("image", response)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun exitBad() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun updateImageInfo(response: ImageData): Boolean {
        val connection = URL("https://api.imgur.com/3/image/" + response.deleteHash).openConnection() as HttpURLConnection
        val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"

        try {
            connection.addRequestProperty("Authorisation", clientHeader)
            connection.addRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.requestMethod = "POST"
            connection.doOutput = true

            val output = OutputStreamWriter(connection.outputStream)
            fun form(name: String, lambda: OutputStreamWriter.()->Unit) {
                output.apply {
                    write("--$boundary\nContent-Disposition: form-data; name=\"$name\"\n\n")
                    lambda()
                    write("\n")
                }
            }

            fun multipart(lambda: ()->Unit) {
                output.apply {
                    write("\n\n")
                    lambda()
                    write("--$boundary\n")
                    close()
                }
            }

            multipart {
                if (nameField.text.isNotBlank())
                    form("title") {
                        write(nameField.text.toString())
                    }
                if (descriptionField.text.isNotBlank())
                    form("description") {
                        write(descriptionField.text.toString())
                    }
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errorResponse = connection.errorStream.reader().readText()
                connection.disconnect()
                Log.d(tag, "An error has occurred while updating photo information: ${
                    connection.responseCode}\n$errorResponse")
                showErrorDialog(errorResponse)
                return false
            }

        } finally {
            connection.disconnect()
        }
        return true
    }

    private fun parseResponse(reader: JsonReader): ImgurUploadResponse {
        var success = false
        var status = -1
        lateinit var data: ImageData
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "success" -> success = reader.nextBoolean()
                "status" -> status = reader.nextInt()
                "data" -> data = parseImageData(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        reader.close()
        return ImgurUploadResponse(success, status, data)
    }

    private fun parseImageData(reader: JsonReader): ImageData {
        var id = ""
        var deleteHash = ""
        var link = ""
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "id" -> id = reader.nextString()
                "deletehash" -> deleteHash = reader.nextString()
                "link" -> link = reader.nextString()
                else -> reader.skipValue()
            }

        }
        reader.endObject()
        return ImageData(id, deleteHash, link)
    }

    private fun enableActions(state: Boolean) {
        //actionBar.setDisplayShowHomeEnabled(state)
        publishButton.isEnabled = state
        nameField.isEnabled = state
        descriptionField.isEnabled = state
        progressSpin.visibility = if (state)
            View.GONE
        else
            View.VISIBLE
    }

    fun onClick(view: View) {
        if (view.id == R.id.publish_button) {
            enableActions(false)
            thread {
                anotherPostPhoto()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        outState!!.putParcelable("fileUri", imageUri)
        super.onSaveInstanceState(outState, outPersistentState)
    }

}
