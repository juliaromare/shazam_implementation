package edu.macalester.comp124.audiofingerprinter;


/**
 * Represents a song and the greatest consecutive count of a certain offset for that song.
 *
 * Created by dakotabaker on 4/25/16.
 */
public class MatchingSong implements Comparable<MatchingSong>{

    private int songID;
    private int maxCount;

    public MatchingSong(int songID, Integer maxCount){
        this.songID = songID;
        this.maxCount = maxCount;
    }

    /**
     * Gets the instance variable maxCount.
     * @return int maxCount.
     */
    public int getMaxCount(){
        return maxCount;
    }

    /**
     * Gets the instance variable songID.
     * @return int songID.
     */
    public int getSongID() {
        return songID;
    }

    /**
     * Compares a MatchingSong another instance of MatchingSong on the instance variable maxCount.
     * @param matchingSong the instance of MatchingSong which we are comparing to.
     * @return 1 if input is smaller than this instance, -1 if input is greater and 0 if they are equal.
     */
    @Override
    public int compareTo(MatchingSong matchingSong) {
        if(matchingSong == null) {
            throw new NullPointerException("matchingSong is null");
        }
        else if(maxCount > matchingSong.getMaxCount()) {
            return 1;
        }
        else if(maxCount  == matchingSong.getMaxCount()) {
            return 0;
        }
        else {
            return -1;
        }
    }
}
