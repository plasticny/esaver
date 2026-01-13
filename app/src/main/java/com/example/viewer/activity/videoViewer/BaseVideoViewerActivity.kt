package com.example.viewer.activity.videoViewer

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.viewer.data.struct.Book
import com.example.viewer.databinding.ActivityVideoViewerBinding

class BaseVideoViewerActivity: AppCompatActivity() {
    private lateinit var rootBinding: ActivityVideoViewerBinding
    private lateinit var player: ExoPlayer

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootBinding = ActivityVideoViewerBinding.inflate(layoutInflater)
        setContentView(rootBinding.root)

        val book = Book.getTmpBook()

        player = ExoPlayer.Builder(baseContext).build()
        rootBinding.playerView.player = player

        val mediaItem = MediaItem.fromUri(book.url)
        val mediaSource = ProgressiveMediaSource
            .Factory(DefaultDataSource.Factory(baseContext))
            .createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}