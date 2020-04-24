package com.jeahwan.audioplayer;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.io.File;

public class AudioPlayerManager {
    private static final String TAG = "AudioPlayerManager";
    private static AudioPlayerManager instance;
    private Context context;
    private SimpleExoPlayer mediaPlayer;
    private DataSource.Factory dataSourceFactory;

    private AudioPlayerManager(Context context) {
        this.context = context;
        createPlayer();
    }

    public static AudioPlayerManager getInstance(Context context) {
        synchronized (AudioPlayerManager.class) {
            if (instance == null) {
                instance = new AudioPlayerManager(context);
            }
        }

        return instance;
    }

    public ExoPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    //设置播放url
    public void setAudioUrl(String audioUrl) {
        try {
            //这是一个代表将要被播放的媒体的MediaSource
            MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(Uri.parse(audioUrl));
            mediaPlayer.prepare(mediaSource);
            mediaPlayer.setPlayWhenReady(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //设置播放file
    public void setAudioFile(File file) {
        try {
            //这是一个代表将要被播放的媒体的MediaSource
            MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(Uri.fromFile(file));
            mediaPlayer.prepare(mediaSource);
            mediaPlayer.setPlayWhenReady(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //开始播放
    public void start() {
        mediaPlayer.setPlayWhenReady(true);
    }

    //判断是否是播放状态
    public boolean isPlaying() {
        int playbackState = mediaPlayer.getPlaybackState();
        return playbackState == SimpleExoPlayer.STATE_READY && mediaPlayer.getPlayWhenReady();
    }

    //播放，带回调事件
    public void playWithCompletionListener(String url, Player.EventListener listener) {
        if (listener != null) {
            mediaPlayer.addListener(listener);
        }
        if (url.startsWith("http")) {
            setAudioUrl(url);
        } else {
            setAudioFile(new File(url));
        }
        mediaPlayer.setPlayWhenReady(true);
    }

    //播放or暂停
    public void playOrPause() {
        if (isPlaying()) {
            mediaPlayer.setPlayWhenReady(false);
        } else {
            mediaPlayer.setPlayWhenReady(true);
        }
    }

    //切换播放速率
    public void switchSpeed(float speed) {
        // API 23 （6.0）以上 ，通过设置Speed改变音乐的播放速率
        if (isPlaying()) {
            // 判断是否正在播放，未播放时，要在设置Speed后，暂停音乐播放
            getMediaPlayer().setPlaybackParameters(new PlaybackParameters(speed));
        } else {
            getMediaPlayer().setPlaybackParameters(new PlaybackParameters(speed));
            getMediaPlayer().setPlayWhenReady(false);
        }
    }

    //停止播放
    public void stop(boolean reset) {
        if (mediaPlayer != null) {
            mediaPlayer.stop(reset);
        }
    }

    //释放资源
    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    //创建一个新的player
    private void createPlayer() {
        // 创建带宽
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        // 创建轨道选择工厂
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        // 创建轨道选择器实例
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        //step2.创建播放器
        mediaPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
        //创建一个DataSource对象，通过它来下载多媒体数据
        dataSourceFactory = new DefaultDataSourceFactory(context, Util.getUserAgent(context, "loader"));
    }
}
