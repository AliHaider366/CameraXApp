package com.example.cameraxapp

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.example.cameraxapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    //private lateinit var cameraController: LifecycleCameraController
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraSelector: CameraSelector
    private var imageCapture: ImageCapture? = null
    private lateinit var imgCaptureExecutor: ExecutorService
    private var imageList: Array<File>? = null
    private var hdrOnFlag: Boolean = false
    private var flashOnFlag: Boolean = false
    private val permissions = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private val cameraPermissionResult =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionGranted ->
            val grant = permissionGranted.entries.all { it.value }
            if (grant) {
                startCamera()
            } else {
                Snackbar.make(
                    binding.root,
                    "The camera permission is necessary",
                    Snackbar.LENGTH_INDEFINITE
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val window = this.window
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        imgCaptureExecutor = Executors.newSingleThreadExecutor()

        cameraPermissionResult.launch(permissions)

        binding.imgCaptureBtn.setOnClickListener {
            takePhoto()
            animateFlash()
        }

        val dcimDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        imageList =
            File(dcimDirectory.absolutePath.toString() + "/CameraX").listFiles() as Array<File>

        if (imageList?.isNotEmpty() == true) {
            Glide.with(this@MainActivity).load(imageList!![imageList!!.size - 1].toUri())
                .into(binding.galleryBtn)
        }

        binding.switchBtn.setOnClickListener {
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                startCamera()
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                startCamera()
            }
        }
        binding.galleryBtn.setOnClickListener {
            val intent = Intent(this, ImageActivity::class.java)
            startActivity(intent)
        }

        binding.imgHdr.setOnClickListener {
            if (hdrOnFlag) {
                binding.imgHdr.setImageDrawable(resources.getDrawable(R.drawable.hdr_off_icon))
                hdrOnFlag = false
                startCamera()
            } else {
                binding.imgHdr.setImageDrawable(resources.getDrawable(R.drawable.hdr_on_icon))
                hdrOnFlag = true
                startCamera()
            }
        }

        binding.imgFlash.setOnClickListener {
            if (flashOnFlag) {
                binding.imgFlash.setImageDrawable(resources.getDrawable(R.drawable.flash_off_icon))
                flashOnFlag = false
                startCamera()
            } else {
                binding.imgFlash.setImageDrawable(resources.getDrawable(R.drawable.flash_on_icon))
                flashOnFlag = true
                startCamera()
            }
        }

    }

//    private fun startCamera() {
//        val preview = binding.preview
//        cameraController = LifecycleCameraController(baseContext)
//        cameraController.bindToLifecycle(this)
//        cameraController.cameraSelector = cameraSelector
//        preview.controller = cameraController
//
//    }

    private fun startCamera() {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.preview.surfaceProvider)
        }

        imageCapture = if (flashOnFlag) {
            ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_ON)
                .build()
        }else{
            ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
        }


        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val extensionsManagerFuture =
                ExtensionsManager.getInstanceAsync(applicationContext, cameraProvider)
            extensionsManagerFuture.addListener(
                {
                    val extensionsManager = extensionsManagerFuture.get()

                    if (hdrOnFlag) {
                        if (extensionsManager.isExtensionAvailable(
                                cameraSelector,
                                ExtensionMode.HDR
                            )
                        ) {
                            Toast.makeText(this@MainActivity, "HDR Available", Toast.LENGTH_SHORT)
                                .show()
                            cameraProvider.unbindAll()

                            val hdrCameraSelector =
                                extensionsManager.getExtensionEnabledCameraSelector(
                                    cameraSelector,
                                    ExtensionMode.HDR
                                )

                            val camera = cameraProvider.bindToLifecycle(
                                this,
                                hdrCameraSelector,
                                imageCapture,
                                preview
                            )

                            binding.preview.setOnTouchListener { _, event ->
                                val factory = binding.preview.meteringPointFactory
                                val point = factory.createPoint(event.x, event.y)
                                animateFocusCircle(event.x, event.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                camera.cameraControl.startFocusAndMetering(action)
                                true
                            }

                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "HDR Not Supported",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }

                    } else {


                        cameraProvider.unbindAll()

                        val camera =
                            cameraProvider.bindToLifecycle(
                                this,
                                cameraSelector,
                                preview,
                                imageCapture
                            )

                        binding.preview.setOnTouchListener { _, event ->

                            val factory = binding.preview.meteringPointFactory
                            val point = factory.createPoint(event.x, event.y)
                            animateFocusCircle(event.x, event.y)
                            val action = FocusMeteringAction.Builder(point).build()
                            camera.cameraControl.startFocusAndMetering(action)
                            true
                        }
                    }


                },
                ContextCompat.getMainExecutor(this)
            )






            try {

            } catch (e: Exception) {
                Log.d("TAG", "Use case binding failed")
            }

        }, ContextCompat.getMainExecutor(this))


    }

    private fun animateFocusCircle(x: Float, y: Float) {


        // Move the focus circle so that its center is at the tap location (x, y)
        val width = binding.focusCircle.width.toFloat()
        val height = binding.focusCircle.height.toFloat()
        binding.focusCircle.x = x - width / 2
        binding.focusCircle.y = y - height / 2

        // Show focus ring
        binding.focusCircle.visibility = View.VISIBLE
        binding.focusCircle.alpha = 1f

        // Animate the focus circle to disappear
        binding.focusCircle.animate()
            .setStartDelay(500)
            .setDuration(300)
            .alpha(0f)
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(p0: Animator) {
                }

                override fun onAnimationEnd(p0: Animator) {
                    binding.focusCircle.visibility = View.INVISIBLE
                }

                override fun onAnimationCancel(p0: Animator) {
                }

                override fun onAnimationRepeat(p0: Animator) {
                }
            })
    }

    private fun takePhoto() {
        imageCapture?.let {
            val folderName = "CameraX"
            val dcimDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val newFolder = File(dcimDirectory, folderName)
            if (!newFolder.exists()) {
                newFolder.mkdirs()
            }

            val file = File(newFolder, "IMG_${System.currentTimeMillis()}.jpg")
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
            it.takePicture(
                outputFileOptions,
                imgCaptureExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        val contentUri = Uri.fromFile(file)
                        mediaScanIntent.data = contentUri
                        this@MainActivity.sendBroadcast(mediaScanIntent)
                        runOnUiThread {
                            Glide.with(this@MainActivity).load(file.toUri())
                                .into(binding.galleryBtn)
                        }
                        //Log.i(TAG, "The image has been saved in ${file.toUri()}")
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(
                            binding.root.context,
                            "Error taking photo",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("TAG", "Error taking photo:$exception")
                    }

                })
        }

    }

    private fun animateFlash() {
        binding.root.postDelayed({
            binding.root.foreground = ColorDrawable(Color.WHITE)
            binding.root.postDelayed({
                binding.root.foreground = null
            }, 50)
        }, 100)
    }

}