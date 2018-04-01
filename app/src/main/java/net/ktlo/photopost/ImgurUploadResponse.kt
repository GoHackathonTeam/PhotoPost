package net.ktlo.photopost

import java.io.Serializable

/**jnj
 * Created by Ktlo on 25.03.2018.
 */
data class ImgurUploadResponse(
    val success: Boolean,
    val status: Int,
    val data: ImageData
)

data class ImageData(
        val id: String,
        val deleteHash: String,
        val link: String
): Serializable