package com.learn.translatorapp

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.learn.translatorapp.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var sourceLanguage: EditText
    private lateinit var targetLanguage: TextView
    private lateinit var sourceButton: MaterialButton
    private lateinit var chooseBtn: MaterialButton
    private lateinit var translateBtn: MaterialButton

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var outputDirectory: File

    companion object {
        private const val TAG = "MAIN_TAG"
        private const val TAG2 = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private var languageArrayList: ArrayList<ModelLanguage>? = null

    private var sourceLanguageCode = "en"
    private var targetLanguageCode = "hi"
    private var sourceLanguageTitle = "English"
    private var targetLanguageTitle = "Hindi"

    private lateinit var translatorOptions: TranslatorOptions
    private lateinit var translator: Translator
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        binding.captureButton.setOnClickListener {
            takePhoto()
        }

        sourceLanguage = findViewById(R.id.sourceLanguage)
        targetLanguage = findViewById(R.id.TargetLanguage)
        sourceButton = findViewById(R.id.sourceButton)
        chooseBtn = findViewById(R.id.chooseBtn)
        translateBtn = findViewById(R.id.translateBtn)

        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait")
        progressDialog.setCanceledOnTouchOutside(false)

        loadAvailableLanguages()

        sourceButton.setOnClickListener {
            sourceLanguageChoose()
        }
        chooseBtn.setOnClickListener {
            targetLanguageChoose()
        }
        translateBtn.setOnClickListener {
            validateData()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG2, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    cropAndRecognizeText(savedUri)
                }
            }
        )
    }

    private fun cropAndRecognizeText(uri: Uri) {
        val bitmap = BitmapFactory.decodeFile(uri.path)
        val viewFinder = binding.viewFinder

        val viewFinderWidth = viewFinder.width
        val viewFinderHeight = viewFinder.height
        val scaleX = (bitmap.width.toFloat() / viewFinder.width.toFloat())
        val scaleY = (bitmap.height.toFloat() / viewFinder.height.toFloat())/3

        // Calculate crop coordinates starting from the center of the bitmap
        val cropWidth = viewFinderWidth * scaleX
        val cropHeight = viewFinderHeight * scaleY
        val cropStartX = (bitmap.width - cropWidth.toInt()) / 2
        val cropStartY = (bitmap.height - cropHeight.toInt()) / 2

        // Crop the bitmap starting from the calculated center coordinates
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            cropStartX,
            cropStartY,
            cropWidth.toInt(),
            cropHeight.toInt()
        )

        // Rotate the cropped bitmap if needed (adjust rotation degrees as per your device)
        val rotatedBitmap = rotateBitmap(croppedBitmap, 270f)

        // Convert rotated bitmap to InputImage for ML Kit
        val image = InputImage.fromBitmap(rotatedBitmap, 0)

        // Initialize text recognizer
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Process the image for text recognition
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Extract recognized text
                val recognizedText = visionText.text
                Log.d(TAG2, "Recognized text: $recognizedText")
                // Set recognized text to EditText
                sourceLanguage.setText(recognizedText)
                showToast("Text recognized and copied to input field")
            }
            .addOnFailureListener { e ->
                Log.e(TAG2, "Text recognition failed: ${e.message}", e)
                showToast("Text recognition failed: ${e.message}")
            }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }



    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG2, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "YourAppName").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private var sourceLanguageText = ""
    private fun validateData() {
        sourceLanguageText = sourceLanguage.text.toString().trim()

        if (sourceLanguageText.isEmpty()) {
            showToast("Enter Text to Translate")
        } else {
            startTranslation()
        }
    }

    private fun startTranslation() {
        progressDialog.setMessage("Translating...")
        progressDialog.show()
        translatorOptions = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguageCode)
            .setTargetLanguage(targetLanguageCode)
            .build()
        translator = Translation.getClient(translatorOptions)

        val downloadConditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        translator.downloadModelIfNeeded(downloadConditions)
            .addOnSuccessListener {
                translator.translate(sourceLanguageText)
                    .addOnSuccessListener { translatedText ->
                        progressDialog.dismiss()
                        targetLanguage.text = translatedText
                    }
                    .addOnFailureListener { e ->
                        progressDialog.dismiss()
                        showToast("Failed to translate due to ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                showToast("Failed to download model due to ${e.message}")
            }
    }

    private fun loadAvailableLanguages() {
        languageArrayList = ArrayList()
        val languageList = TranslateLanguage.getAllLanguages()
        for (languageCode in languageList) {
            val languageTitle = Locale(languageCode).displayName
            val modelLanguage = ModelLanguage(languageCode, languageTitle)
            languageArrayList!!.add(modelLanguage)
        }
    }

    private fun sourceLanguageChoose() {
        val popupMenu = PopupMenu(this, sourceButton)
        for (i in languageArrayList!!.indices) {
            popupMenu.menu.add(Menu.NONE, i, i, languageArrayList!![i].languageTitle)
        }
        popupMenu.show()
        popupMenu.setOnMenuItemClickListener { menuItem ->
            val position = menuItem.itemId
            sourceLanguageCode = languageArrayList!![position].languageCode
            sourceLanguageTitle = languageArrayList!![position].languageTitle
            sourceButton.text = sourceLanguageTitle
            return@setOnMenuItemClickListener true
        }
    }

    private fun targetLanguageChoose() {
        val popupMenu = PopupMenu(this, chooseBtn)
        for (i in languageArrayList!!.indices) {
            popupMenu.menu.add(Menu.NONE, i, i, languageArrayList!![i].languageTitle)
        }
        popupMenu.show()
        popupMenu.setOnMenuItemClickListener { menuItem ->
            val position = menuItem.itemId
            targetLanguageCode = languageArrayList!![position].languageCode
            targetLanguageTitle = languageArrayList!![position].languageTitle
            chooseBtn.text = targetLanguageTitle
            return@setOnMenuItemClickListener true
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
