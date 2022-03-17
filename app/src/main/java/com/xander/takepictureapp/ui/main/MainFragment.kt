package com.xander.takepictureapp.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.xander.takepictureapp.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    private var uri: Uri? = null

    private val getPicture = registerForActivityResult(TakePictureContract()) { imageUri: Uri? ->
        var file = File(FileUtils.getCurrentPhotoPath())

        // if we take photo from camera imageUri will be null and we set uri before
        // if we take photo from gallery next step will be set imageUri
        if (imageUri != null) {
            uri = imageUri
            file = FileUtils.getFileFromUri(requireContext(), imageUri)
        }

        val size = file.length() / 1024

        Toast.makeText(requireContext(), "File size: $size", Toast.LENGTH_SHORT).show()

        val compressedFile = FileUtils.compressFile(file)
        val compressedSize = compressedFile.length() / 1024

        Toast.makeText(
            requireContext(),
            "compressed file size: $compressedSize",
            Toast.LENGTH_SHORT
        ).show()

        // load image for preview
        Glide.with(requireActivity()).load(compressedFile).into(view?.findViewById(R.id.picture)!!)
    }

    private val requestSinglePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                uri = FileUtils.getTempFileUri(requireContext())
                getPicture.launch(uri)
            } else {
                //request needed permissions again
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.take_picture).setOnClickListener {
            requestSinglePermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////

object FileUtils {

    private const val FILE_PROVIDER = "com.xander.takepictureapp.fileprovider"
    private const val FILE_PREFIX = "JPEG_"
    private const val FILE_SUFFIX = ".jpg"
    private const val FILE_DATE_PATTERN = "yyyyMMdd_HHmmss"
    private const val REQUIRED_SIZE = 75

    private var currentPhotoPath = ""

    fun getTempFileUri(context: Context): Uri {
        val output = createImageFile(context)
        return FileProvider.getUriForFile(
            context,
            FILE_PROVIDER,
            output
        )
    }

    @SuppressLint("SimpleDateFormat")
    fun getFileFromUri(context: Context, uri: Uri): File {
        val timeStamp = SimpleDateFormat(FILE_DATE_PATTERN).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile("$FILE_PREFIX${timeStamp}_", FILE_PREFIX, storageDir).apply {
            outputStream().use {
                context.contentResolver.openInputStream(uri)?.copyTo(it)
            }
        }
        return file
    }

    fun getCurrentPhotoPath() = currentPhotoPath

    fun compressFile(file: File): File {
        return try {
            val exifOrientation = ExifInterface(file.absolutePath)
                .getAttribute(ExifInterface.TAG_ORIENTATION)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inSampleSize = 6
            }
            var inputStream = FileInputStream(file)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            var scale = 1
            while (options.outWidth / scale / 2 >= REQUIRED_SIZE && options.outHeight / scale / 2 >= REQUIRED_SIZE) {
                scale *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            inputStream = FileInputStream(file)
            val selectedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream.close()

            file.createNewFile()
            val outputStream = FileOutputStream(file)
            selectedBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

            exifOrientation?.let { orientation ->
                ExifInterface(file.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation)
                    saveAttributes()
                }
            }

            file
        } catch (e: Exception) {
            file
        }
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat(FILE_DATE_PATTERN).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "$FILE_PREFIX${timeStamp}_",
            FILE_SUFFIX,
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////

class TakePictureContract : ActivityResultContract<Uri, Uri?>() {

    private companion object {
        const val MIMETYPE_IMAGES = "image/*"
    }

    override fun createIntent(context: Context, input: Uri?): Intent {
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
            .apply { type = MIMETYPE_IMAGES }
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)

        return Intent.createChooser(galleryIntent, "Select from:")
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (intent == null || resultCode != Activity.RESULT_OK) return null
        return intent.data
    }

    override fun getSynchronousResult(context: Context, input: Uri?): SynchronousResult<Uri?>? {
        return null
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////