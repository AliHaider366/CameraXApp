package com.example.cameraxapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import com.example.cameraxapp.databinding.ActivityImageBinding
import java.io.File

class ImageActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityImageBinding.inflate(layoutInflater)
    }

    private lateinit var imageList: Array<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

//        val directory = File(externalMediaDirs[0].absolutePath)
//        val files = directory.listFiles() as Array<File>

        val dcimDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        imageList = File(dcimDirectory.absolutePath.toString() + "/CameraX").listFiles() as Array<File>

        val adapter = ImageAdapter(imageList.reversedArray())
        binding.viewPager.adapter = adapter
    }

}