package com.example.mediastudyproject.activity

import android.os.Bundle
import android.util.Log
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.SimpleAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.R
import com.example.mediastudyproject.adapter.ListAdapter
import com.example.mediastudyproject.bean.Song
import java.io.File
import java.io.FileFilter

class VideoListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_list)
        listView = findViewById(R.id.list_view)
        findFiles()
    }

    private fun findFiles() {
        val fileDir = File(filesDir,"")
        if (fileDir.exists() && fileDir.isDirectory) {
            val listFiles = fileDir.listFiles(FileFilter { it.path.endsWith(".mp4") && it.length() > 0 })
            val flatMap = listFiles.flatMap { arrayListOf(Song(it.name, it.path)) }
            val arrayList = ArrayList<Song>()
            arrayList.addAll(flatMap)
            val listAdapter = ListAdapter(this,arrayList)
            listView.adapter = listAdapter
        }
    }
}
