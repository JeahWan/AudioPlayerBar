package com.jeahwan.audioplayer;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.jeahwan.audioplayer.databinding.DialogPlaySpeedBinding;
import com.jeahwan.audioplayer.utils.BigDecimalUtils;
import com.jeahwan.audioplayer.utils.DensityUtil;
import com.jeahwan.audioplayer.utils.SPUtils;

import java.util.ArrayList;
import java.util.List;

public class MediaPlayerManager {

    private static ExoPlayer mediaPlayer;
    private static AudioPlayerManager audioPlayerManager;
    private static MediaPlayerManager instance;
    private static Context mContext;
    private static List<AudioList.Audio> audioList;
    public String mPlayingId;
    public String mAudioUrl;
    public String mSectionId;
    OnCloseListener mOnCloseListener;
    private CountDownTimer countDownTimer;
    private boolean isChanging;
    private View playView;
    private PlayerListener mPlayerListener;
    private Player.EventListener playStatusListener;
    private boolean mCloseIsStop;
    private String mSectionTitle;
    private String mCourseTitle;
    private int mPlayType;
    private boolean mShowClose;
    private int mBottomMargin;
    private boolean isReport, percentUpload;
    public static final String PLAY_SPEED = "PLAY_SPEED";

    private MediaPlayerManager() {

    }

    public static MediaPlayerManager getInstance(Context context) {
        mContext = context;
        synchronized (MediaPlayerManager.class) {
            if (instance == null) {
                audioPlayerManager = AudioPlayerManager.getInstance(context);
                mediaPlayer = audioPlayerManager.getMediaPlayer();
                instance = new MediaPlayerManager();
                audioList = new ArrayList<>();
            }
        }
        return instance;
    }

    public void checkPlayStatus(int marginBottom, boolean showCloseBtn, boolean closeIsStop) {
        if (isPlaying()) {
            //避免首页隐藏状态 进入别的页面没有显示了
            playView.setAlpha(1);
            playView.setTranslationY(0);
            if (!showCloseBtn) {
                changeCloseBtnStatus(false, false);
            }
            mShowClose = showCloseBtn;
            changeCloseBtnStatus(showCloseBtn && !audioPlayerManager.isPlaying(), false);
            mCloseIsStop = closeIsStop;
            //正在播放 加载UI到页面
            removeView();
            // 高度为200pix
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            // 在窗口的底部
            mBottomMargin = marginBottom;
            if (mContext != null) {
                layoutParams.bottomMargin = DensityUtil.getNavigationBarHeight(mContext) + marginBottom;
            }
            layoutParams.gravity = Gravity.BOTTOM;
            if (playView.getParent() == null && mContext != null) {
                ((ViewGroup) ((Activity) mContext).getWindow().getDecorView()).addView(playView, 1, layoutParams);
            }
        }
    }

    public void removeView() {
        if (mContext != null) {
            ((ViewGroup) ((Activity) mContext).getWindow().getDecorView()).removeView(playView);
        }
    }

    public void setAudioList(List<AudioList.Audio> audioList) {
        MediaPlayerManager.audioList = audioList;
    }

    public void showPlay(String audioUrl, String sectionId, String courseTitle, String sectionTitle, int playType, boolean showClose, int bottomMargin) {
        if (sectionId.equals(mPlayingId)) {
            //暂停状态 加载ui
            checkPlayStatus(bottomMargin, showClose, true);
            return;
        }
        //重新播放
        if (playType == 0) {
            //单课 清空列表
            audioList.clear();
        }
        removeView();

        isReport = false;
        percentUpload = false;
        mAudioUrl = audioUrl;
        mSectionId = sectionId;
        mPlayingId = sectionId;
        mCourseTitle = courseTitle;
        mSectionTitle = sectionTitle;
        mPlayType = playType;
        mBottomMargin = bottomMargin;

        changeCloseBtnStatus(false, false);
        mShowClose = showClose;

        if (mContext == null) {
            return;
        }
        playView = ((Activity) mContext).getLayoutInflater().inflate(R.layout.item_play, null);
        //透明度处理
        playView.findViewById(R.id.ll_root).setAlpha(0.96f);
        audioPlayerManager.switchSpeed(1.0f);
        if (playStatusListener == null) {
            //监听必须处理为初始化一次 不然会多次回调
            playStatusListener = new Player.EventListener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    //开始播放 这里会回调多次 判断开始播放不靠谱
                    if (playbackState == PlaybackState.STATE_PLAYING) {
                        //设置为保存的倍速
                        audioPlayerManager.switchSpeed(SPUtils.get().getFloat(PLAY_SPEED, 1));
                        ((SeekBar) playView.findViewById(R.id.seekbar)).setMax((int) mediaPlayer.getDuration());
                        ((ImageView) playView.findViewById(R.id.iv_play)).setImageResource(R.drawable.icon_audio_player_playing);
                    } else if (playWhenReady && playbackState == PlaybackState.STATE_FAST_FORWARDING) {
                        //播放完毕的UI及逻辑处理
                        mPlayingId = "";
                        countDownTimer.onFinish();
                        ((SeekBar) playView.findViewById(R.id.seekbar)).setProgress(0);
                        ((ImageView) playView.findViewById(R.id.iv_play)).setImageResource(R.drawable.icon_audio_player_pause);
                        ((TextView) playView.findViewById(R.id.tv_cur_time)).setText("00:00");
                        ((TextView) playView.findViewById(R.id.tv_total_time)).setText(getTotalTime());
                        if (playType != 0) {
                            //playType不为0 即为音频列表 处理顺序播放
                            if (mPlayerListener != null) {
                                mPlayerListener.onComplate(mSectionId);
                            }
                            if (audioList.size() > 0) {
                                showPlay(audioList.get(0).audio, audioList.get(0).audioCode, mCourseTitle, audioList.get(0).title, playType, mShowClose, mBottomMargin);
                                audioList.remove(0);
                                return;
                            }
                        }
                        //list中没有了 点击重新播放
                        changeCloseBtnStatus(true, true);
                        playView.findViewById(R.id.iv_play).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                changeCloseBtnStatus(false, true);
                                showPlay(mAudioUrl, mSectionId, mCourseTitle, mSectionTitle, mPlayType, mShowClose, mBottomMargin);
                                if (mPlayerListener != null) {
                                    mPlayerListener.onPlayStatusChange(mediaPlayer.getDuration() / 1000,
                                            mediaPlayer.getCurrentPosition() / 1000, true);
                                }
                            }
                        });
                    }
                }
            };
        }
        if (!TextUtils.isEmpty(audioUrl)) {
            audioPlayerManager.playWithCompletionListener(audioUrl, playStatusListener);
        }
        ((TextView) playView.findViewById(R.id.tv_title)).setText(sectionTitle);
        ((TextView) playView.findViewById(R.id.tv_course)).setText(courseTitle);
        playView.findViewById(R.id.iv_play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioPlayerManager.playOrPause();
                if (audioPlayerManager.isPlaying()) {
                    changeCloseBtnStatus(false, true);
                    ((ImageView) playView.findViewById(R.id.iv_play)).setImageResource(R.drawable.icon_audio_player_playing);
                } else {
                    //暂停
                    changeCloseBtnStatus(true, true);
                    ((ImageView) playView.findViewById(R.id.iv_play)).setImageResource(R.drawable.icon_audio_player_pause);
                }
                if (mPlayerListener != null) {
                    mPlayerListener.onPlayStatusChange(mediaPlayer.getDuration() / 1000,
                            mediaPlayer.getCurrentPosition() / 1000, audioPlayerManager.isPlaying());
                }
            }
        });
        //上次设定的倍速
        ((TextView) playView.findViewById(R.id.tv_speed)).setText(BigDecimalUtils.doubleTrans(
                Double.parseDouble(SPUtils.get().getFloat(PLAY_SPEED, 1) + "")) + "倍");
        playView.findViewById(R.id.tv_speed).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //检查版本 23 M 以上才能支持倍速
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    Toast.makeText(mContext, "当前系统版本不支持倍速播放", Toast.LENGTH_SHORT).show();
                    return;
                }
                new BaseDialog<>(((Activity) mContext), R.layout.dialog_play_speed, new BaseDialog.onCreateView<DialogPlaySpeedBinding>() {
                    @Override
                    public void createView(DialogPlaySpeedBinding binding, final Dialog dialog) {
                        //view中tag存储了倍数值，hint存储了显示值，即XX倍
                        List<TextView> speedTv = new ArrayList<>();
                        speedTv.add(binding.tvSpeed1);
                        speedTv.add(binding.tvSpeed2);
                        speedTv.add(binding.tvSpeed3);
                        speedTv.add(binding.tvSpeed4);
                        Float[] speedArr = new Float[]{0.75f, 1f, 1.5f, 2.0f};
                        for (int i = 0; i < speedTv.size(); i++) {
                            speedTv.get(i).setTag(speedArr[i]);
                            if (((TextView) playView.findViewById(R.id.tv_speed)).getText().equals(speedTv.get(i).getHint())) {
                                //当前选择的速度 设为红色
                                speedTv.get(i).setTextColor(0xffff1001);
                            }

                        }
                        View.OnClickListener speedChange = new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                audioPlayerManager.switchSpeed((Float) v.getTag());
                                ((TextView) playView.findViewById(R.id.tv_speed)).setText(((TextView) v).getHint());
                                SPUtils.get().putFloat(PLAY_SPEED, (Float) v.getTag());
                                dialog.dismiss();
                            }
                        };
                        binding.tvSpeed1.setOnClickListener(speedChange);
                        binding.tvSpeed2.setOnClickListener(speedChange);
                        binding.tvSpeed3.setOnClickListener(speedChange);
                        binding.tvSpeed4.setOnClickListener(speedChange);
                        binding.tvClose.setOnClickListener(v1 -> dialog.dismiss());
                    }
                })
                        .setWidth(ViewGroup.LayoutParams.MATCH_PARENT)
                        .setGravity(Gravity.BOTTOM)
                        .show();
            }
        });

        enlargeSeekBar(playView.findViewById(R.id.cl_root), playView.findViewById(R.id.seekbar));

        countDownTimer = new CountDownTimer(Integer.MAX_VALUE, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (playView != null) {
                    if (getCurrentTime() != null) {
                        ((TextView) playView.findViewById(R.id.tv_cur_time)).setText(getCurrentTime());
                    }
                    if (getTotalTime() != null) {
                        if (!isReport) {
                            isReport = true;
                        }
                        ((TextView) playView.findViewById(R.id.tv_total_time)).setText(getTotalTime());
                    }
                    double playPercent = (BigDecimalUtils.format((double) mediaPlayer.getCurrentPosition() / (double) mediaPlayer.getDuration(), 1));
                    if (playPercent >= 0.9 && !percentUpload) {
                        percentUpload = true;
                        //回调监听
                        if (mPlayerListener != null) {
                            mPlayerListener.playPercent(playPercent);
                        }
                    }
                    if (isChanging) return;
                    ((SeekBar) playView.findViewById(R.id.seekbar)).setProgress((int) mediaPlayer.getCurrentPosition());
                }
            }

            @Override
            public void onFinish() {

            }
        }.start();
        ((SeekBar) playView.findViewById(R.id.seekbar)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isChanging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isChanging = false;
                mediaPlayer.seekTo(seekBar.getProgress());
                if (!audioPlayerManager.isPlaying()) {
                    audioPlayerManager.playOrPause();
                    changeCloseBtnStatus(false, true);
                    ((ImageView) playView.findViewById(R.id.iv_play)).setImageResource(R.drawable.icon_audio_player_playing);
                }
            }
        });
        playView.findViewById(R.id.iv_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mIsClose代表点击关闭是否要清空当前播放
                if (mCloseIsStop) {
                    mPlayingId = "";
                }
                //暂停
                if (audioPlayerManager.isPlaying()) {
                    playView.findViewById(R.id.iv_play).performClick();
                }
                //隐藏view
                if (mContext != null) {
                    ((ViewGroup) ((Activity) mContext).getWindow().getDecorView()).removeView(playView);
                }
                //关闭监听
                if (mOnCloseListener != null) {
                    mOnCloseListener.onClose();
                }
            }
        });
        // 高度为200pix
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        // 在窗口的底部
        layoutParams.gravity = Gravity.BOTTOM;
        // 首次显示取导航栏底部高度 否则取缓存的margin高度
        layoutParams.bottomMargin = DensityUtil.getNavigationBarHeight(mContext) + mBottomMargin;
        if (playView.getParent() == null) {
            ((ViewGroup) ((Activity) mContext).getWindow().getDecorView()).addView(playView, 1, layoutParams);
        }
    }

    private void changeCloseBtnStatus(boolean isShow, boolean isAnim) {
        if (!mShowClose) return;
        if (playView == null || playView.findViewById(R.id.iv_close) == null || mContext == null) {
            return;
        }
        ObjectAnimator translationX = new ObjectAnimator().ofFloat(playView.findViewById(R.id.iv_close), "translationX",
                DensityUtil.dp2px(mContext, isShow ? -50 : 0), DensityUtil.dp2px(mContext, isShow ? 0 : -50));
        translationX.setInterpolator(isShow ? new OvershootInterpolator() : new AnticipateOvershootInterpolator());
        translationX.setDuration(isAnim ? 800 : 0);  //设置动画时间

        ValueAnimator alphaY = new ValueAnimator().ofInt(DensityUtil.dp2px(mContext, isShow ? 12 : 48), DensityUtil.dp2px(mContext, isShow ? 48 : 12));
        alphaY.setInterpolator(isShow ? new OvershootInterpolator() : new AnticipateOvershootInterpolator());
        alphaY.addUpdateListener(valueAnimator -> {
            playView.findViewById(R.id.tv_title).setPadding((Integer) valueAnimator.getAnimatedValue(), 0, 0, 0);
            playView.findViewById(R.id.tv_course).setPadding((Integer) valueAnimator.getAnimatedValue(), 0, 0, 0);
        });
        alphaY.setDuration(isAnim ? 800 : 0);  //设置动画时间

        translationX.start();
        alphaY.start();
    }

    /**
     * 播放中返回view
     *
     * @return
     */
    public View getPlayer() {
        return isPlaying() ? playView : null;
    }

    public int getPlayerHeight() {
        return playView.getMeasuredHeight();
    }

    public String getCurrentTime() {
        long position = mediaPlayer.getCurrentPosition();
        return calculateTime(position / 1000);
    }

    public String getTotalTime() {
        if (mediaPlayer.getDuration() < 0) {
            return null;
        }
        long duration2 = mediaPlayer.getDuration() / 1000;
        return calculateTime(duration2);
    }

    //计算播放时间
    public String calculateTime(long time) {
        long minute;
        long second;
        if (time > 60) {
            minute = time / 60;
            second = time % 60;
            //分钟再0~9
            if (minute >= 0 && minute < 10) {
                //判断秒
                if (second >= 0 && second < 10) {
                    return "0" + minute + ":" + "0" + second;
                } else {
                    return "0" + minute + ":" + second;
                }
            } else {
                //分钟大于10再判断秒
                if (second >= 0 && second < 10) {
                    return minute + ":" + "0" + second;
                } else {
                    return minute + ":" + second;
                }
            }
        } else if (time < 60) {
            second = time;
            if (second >= 0 && second < 10) {
                return "00:" + "0" + second;
            } else {
                return "00:" + second;
            }
        }
        return null;
    }

    private void enlargeSeekBar(ConstraintLayout mCurrentView, SeekBar mSeekBar) {
        mCurrentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Rect seekRect = new Rect();
                mSeekBar.getHitRect(seekRect);

                if ((event.getY() >= (seekRect.top - 10)) && (event.getY() <= (seekRect.bottom + 10))) {

                    float y = seekRect.top + seekRect.height() / 2;
                    //seekBar only accept relative x
                    float x = event.getX() - seekRect.left;
                    if (x < 0) {
                        x = 0;
                    } else if (x > seekRect.width()) {
                        x = seekRect.width();
                    }
                    MotionEvent me = MotionEvent.obtain(event.getDownTime(), event.getEventTime(),
                            event.getAction(), x, y, event.getMetaState());
                    return mSeekBar.onTouchEvent(me);
                }
                return false;
            }
        });
    }

    public MediaPlayerManager setOnCloseListener(OnCloseListener onCloseListener) {
        mOnCloseListener = onCloseListener;
        return this;
    }

    /**
     * showPlay前先release一下 防止串台
     */
    public void release() {
        if (audioPlayerManager.isPlaying()) {
            playView.findViewById(R.id.iv_close).performClick();
        }
    }

    public boolean playOrPause() {
        if (playView != null) {
            playView.findViewById(R.id.iv_play).performClick();
        }
        return audioPlayerManager != null && audioPlayerManager.isPlaying();
    }

    public boolean isPlaying() {
        return !TextUtils.isEmpty(mPlayingId);
    }

    public String getPlayingId() {
        return mPlayingId != null ? mPlayingId : "";
    }

    public void setPlayerListener(PlayerListener playerListener) {
        this.mPlayerListener = playerListener;
    }

    public interface PlayerListener {
        void onComplate(String audioCode);

        void onPlayStatusChange(long totalTime, long curTime, boolean isPlay);

        void playPercent(double percent);
    }

    public interface OnCloseListener {
        void onClose();
    }
}
