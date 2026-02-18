package com.example.viewer.activity.videoViewer

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.viewer.data.struct.item.Item
import com.example.viewer.databinding.ActivityVideoViewerBinding
import com.example.viewer.struct.ProfileItem
import java.io.File

/**
 * LongExtra: itemId (-1 for temp item)
 */
class BaseVideoViewerActivity: AppCompatActivity() {
    private lateinit var rootBinding: ActivityVideoViewerBinding
    private lateinit var player: ExoPlayer
    private lateinit var videoUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootBinding = ActivityVideoViewerBinding.inflate(layoutInflater)
        setContentView(rootBinding.root)

        val itemId = intent.getLongExtra("itemId", -1)
        videoUrl = if (itemId == -1L) {
            ProfileItem.getTmp().videoData!!.videoUrl
        } else {
            File(Item.getFolder(baseContext, itemId), "video").absolutePath
        }

        preparePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    @OptIn(UnstableApi::class)
    fun preparePlayer () {
        player = ExoPlayer.Builder(baseContext).build()
        rootBinding.playerView.player = player

        val mediaItem = MediaItem.fromUri(videoUrl)
        val mediaSource = ProgressiveMediaSource
            .Factory(DefaultDataSource.Factory(baseContext))
            .createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }
}