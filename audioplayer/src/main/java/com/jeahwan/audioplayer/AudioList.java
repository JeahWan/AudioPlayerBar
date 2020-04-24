package com.jeahwan.audioplayer;

import java.util.List;

public class AudioList {
    public String topPic;
    public List<Audio> dataList;

    public class Audio {
        public String audioCode;
        public String title;
        public String publishDate;
        public String audio;
        public String learnedCount;
        public boolean lastPlay;
        public double lastPlayPercentage;
        public boolean isPlaying;
    }
}
