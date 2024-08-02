package com.learn.translatorapp

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.EditText
import android.widget.ImageView
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
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener

class MainActivity : AppCompatActivity() {

    private lateinit var sourceLanguage: EditText
    private lateinit var targetLanguage: TextView
    private lateinit var sourceButton: MaterialButton
    private lateinit var chooseBtn: MaterialButton
    private lateinit var translateBtn: MaterialButton
    private lateinit var saveHistoryBtn: MaterialButton
    private lateinit var historyBtn: ImageView
    private lateinit var micButton: ImageView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var micSnackbar: Snackbar

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
    private lateinit var dbHelper: TranslationDBHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
        dbHelper = TranslationDBHelper(this)

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
        saveHistoryBtn = findViewById(R.id.saveHistoryBtn)
        historyBtn = findViewById(R.id.historyBtn)

        micButton = findViewById(R.id.mic_button)

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

        saveHistoryBtn.setOnClickListener {
            saveTranslation("Text:\n\t${sourceLanguage.text.toString()}", "Translation:\n\t${targetLanguage.text.toString()}")
            navigateToHistory()
        }

        historyBtn.setOnClickListener {
            navigateToHistory()
        }

        micButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSIONS)
            } else {
                startListening()
            }
        }

        // Initialize Snackbar
        micSnackbar = Snackbar.make(binding.root, "Listening...", Snackbar.LENGTH_INDEFINITE)

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                micSnackbar.show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                micSnackbar.dismiss()
            }
            override fun onError(error: Int) {
                micSnackbar.dismiss()
                showToast("Speech recognition error")
            }

            override fun onResults(results: Bundle?) {
                micSnackbar.dismiss()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    if (it.isNotEmpty()) {
                        val recognizedText = it[0]
                        sourceLanguage.setText(recognizedText)
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

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

        val cropWidth = viewFinderWidth * scaleX
        val cropHeight = viewFinderHeight * scaleY
        val cropStartX = (bitmap.width - cropWidth.toInt()) / 2
        val cropStartY = (bitmap.height - cropHeight.toInt()) / 2

        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            cropStartX,
            cropStartY,
            cropWidth.toInt(),
            cropHeight.toInt()
        )

        val rotatedBitmap = rotateBitmap(croppedBitmap, 270f)

        val image = InputImage.fromBitmap(rotatedBitmap, 0)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val recognizedText = visionText.text
                Log.d(TAG2, "Recognized text: $recognizedText")
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
        speechRecognizer.destroy()
    }
    private var sourceLanguageText = ""
    private fun validateData() {
        val sourceLanguageText = sourceLanguage.text.toString().trim()

        if (sourceLanguageText.isEmpty()) {
            showToast("Please enter text to translate")
            return
        }

        if (sourceLanguageCode == targetLanguageCode) {
            showToast("Source and target languages are the same")
            return
        }

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

    // Function to save translation history
    private fun saveTranslation(sourceText: String, translatedText: String) {
        dbHelper.insertTranslation(sourceText, translatedText)
        showToast("Translation saved to history")
    }

    // Navigate to HistoryActivity
    private fun navigateToHistory() {
        val intent = Intent(this, HistoryActivity::class.java)
        startActivity(intent)
    }

    private fun startListening() {
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLanguageCode)
        speechRecognizer.startListening(speechRecognizerIntent)
    }
}