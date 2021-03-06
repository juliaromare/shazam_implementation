package edu.macalester.comp124.audiofingerprinter;

import java.io.File;
import java.util.*;

/**
 * Creates a "fingerprint" of an audio sample to compare to songs from a SongDatabase and recognize the song.
 * Created by dakotabaker on 4/18/16.
 */

public class Fingerprinter implements AudioFingerprinter {

    private SongDatabase songDatabase; // A SongDatabase object containing songs to process and compare the sample to.
    private final int[] RANGE; // An array of integers that are the boundaries of the key frequency ranges.
    private static final int FUZ_FACTOR = 2; // Handles background noise.

    /**
     * Constructor for the Fingerprinter. Initializes RANGE.
     * @param songDB instance of SongDatabase to set the instance variable, songDatabase, to.
     */
    public Fingerprinter(SongDatabase songDB){
        this.songDatabase = songDB;
        RANGE = new int[] {40, 80, 120, 180, 300}; // frequencies for ranges of different tones.

    }


    /**
     * Returns the database of songs that Fingerprinter uses to recognize a sample.
     * @return the SongDatabase instance variable of the class.
     */
    @Override
    public SongDatabase getSongDB() {
        return songDatabase;
    }


    /**
     * Given an array of bytes representing a song, this method will return a list of song names with matching fingerprints.
     * The algorithm is as follows:
     *      - Convert the audio to the frequency domain
     *      - For each slice of time, determine key points, and create a hash fingerprint
     *      - Find matching datapoints from the song database.
     *      - Calculate the number of matching points for each candidate song
     *      - return a list of song names in order of most matches to least matches.
     * @param audioData array of bytes representing a song
     * @return A list of song names with matching fingerprints, sorted in order from most likely match to least likely match.
     */
    @Override
    public List<String> recognize(byte[] audioData) {
        List<MatchingSong> matchingSongs;

        double [][] sample = songDatabase.convertToFrequencyDomain(audioData);
        long [][] keyPoints = determineKeyPoints(sample);
        HashMap<Integer, HashMap<Integer, Integer>> matchCounts = new HashMap<Integer, HashMap<Integer, Integer>>();

        int sampleTime = 0;
        for(long[] time : keyPoints) {

            long hashTag = hash(time);
            List <DataPoint> songDBDataPoints = songDatabase.getMatchingPoints(hashTag); // Trying to match songs and time offsets to the sample 4/21

            if (songDBDataPoints != null) {
                for (DataPoint dp : songDBDataPoints) {
                    int sID = dp.getSongId();
                    int dBTime = dp.getTime();
                    int offset = Math.abs(sampleTime - dBTime);
                    if (matchCounts.containsKey(sID)) {
                        HashMap<Integer, Integer> offsetCount = matchCounts.get(sID);
                        if (offsetCount.containsKey(offset)) {
                            int count = offsetCount.get(offset);
                            count++;
                            offsetCount.put(offset, count);
                        } else {
                            offsetCount.put(offset, 1);
                        }
                    }
                    //If song doesn't have a match, create a hashmap for that song and add offset and count to hashmap within it.
                    else {
                        HashMap<Integer, Integer> offsetCount = new HashMap<Integer, Integer>();
                        offsetCount.put(offset, 1);
                        matchCounts.put(sID, offsetCount);
                    }
                }
            }
            sampleTime++;
        }

        matchingSongs = createMatchingSongList(matchCounts);

        Collections.sort(matchingSongs);
        Collections.reverse(matchingSongs);

       return songsToStringList(matchingSongs);
    }



    /**
     * Creates a MatchingSong with the songID and greatest consecutive offset count.
     * @param songID int of the song's ID.
     * @param offsetCount HashMap with keys that are time offsets and values that are their counts.
     * @return a MatchingSong holding the songID and the maximum offset count for this song.
     */
    private MatchingSong createMatchingSong(int songID, HashMap<Integer, Integer> offsetCount){
        int max = -1;
        for(int offset : offsetCount.keySet()){
            int value = offsetCount.get(offset);
            if(value > max){
                max = value;
            }
        }
        return new MatchingSong(songID, max);
    }


    /**
     * Creates an ArrayList of MatchingSongs.
     * @param matchCounts HashMap with keys that are the songIDs and values that are HashMaps of time offsets and their counts.
     * @return an ArrayList of MatchingSong objects with their greatest consecutive offset count.
     */
    private List<MatchingSong> createMatchingSongList(HashMap<Integer, HashMap<Integer, Integer>> matchCounts){
        List<MatchingSong> matchingSongs = new ArrayList<MatchingSong>();

        for (int songID : matchCounts.keySet()) {
            HashMap<Integer, Integer> offsetCount = matchCounts.get(songID);
            matchingSongs.add(createMatchingSong(songID, offsetCount));
        }

        return matchingSongs;
    }


    /**
     * Converts a List of MatchingSongs into a list of strings containing the song title and
     * greatest consecutive offset count.
     * @param matchingSongs a List of MatchingSong objects.
     * @return rankedMatches, the List of song strings containing song titles and their maximum offset counts.
     */
    private List<String> songsToStringList(List<MatchingSong> matchingSongs){
        List<String> rankedMatches = new ArrayList<>();

        for(MatchingSong matchingSong : matchingSongs) {
            StringBuilder nameAndMatches = new StringBuilder();
            String name = songDatabase.getSongName(matchingSong.getSongID());
            int matches = matchingSong.getMaxCount();
            nameAndMatches.append(name + ": with " + matches + " matches.");
            rankedMatches.add(nameAndMatches.toString());
        }
        return rankedMatches;
    }

    /**
     * Overloaded method given a file object to recognize.
     * Hint: get the raw audio data from the file and call the other overloaded recognize method.
     * @param fileIn
     * @return
     */
    @Override
    public List<String> recognize(File fileIn) {

        return recognize(songDatabase.getRawData(fileIn));

    }

    /**
     * Given a 2D array of frequency information over time, returns the keypoints.
     * @param results, an array of frequency data. The first index corresponds with a slice of time, the second with the frequency.
     *                 The data is represented as complex numbers with interleaved real and imaginary parts. For example, to get the
     *                 magnitude of a specific frequency:
     *                      double re = results[time][2*freq];
     *                      double im = results[time][2*freq+1];
     *                       double mag = Math.log(Math.sqrt(re * re + im * im) + 1);
     * @return a 2D array where the first index represents the time slice, and the second index contains the highest frequencies
     *          for the following ranges with that time slice: 30 Hz - 40 Hz, 40 Hz - 80 Hz and 80 Hz - 120 Hz for the low tones (covering bass guitar,
     *          for example), and 120 Hz - 180 Hz and 180 Hz - 300 Hz for the middle and higher tones (covering vocals and most other instruments).
     */
    @Override
    public long[][] determineKeyPoints(double[][] results) {
        double [][] highMag = new double[results.length][5]; // First index is time slice, second index is index of frequency ranges, stores highest MAGNITUDE in the given range.
        long [][] keyPoints = new long[results.length][5]; // First index is time slice, second index is index of frequency ranges, stores highest FREQUENCY in the given range.

        for (int t = 0; t < results.length; t++) {
            for (int freq = 40; freq < 300 ; freq++) {
                double re = results[t][2*freq];
                double im = results[t][2*freq+1];
                // Get the magnitude
                double mag = Math.log(Math.sqrt(re * re + im * im) + 1);

                // Find out which range we are in:
                int index = getIndex(freq);

                // Save the highest magnitude and corresponding frequency:
                if (mag > highMag[t][index]) {
                    highMag[t][index] = mag;
                    keyPoints[t][index] = freq;
                }
            }
        }
        return keyPoints;
    }

    /**
     * Determines which range the frequency is in.
     * @param freq a double of a frequency value in the frequency domain.
     * @return the index for the frequency range that this input frequency is in.
     */
    public int getIndex(double freq) {
        int i = 0;
        while (RANGE[i] < freq)
            i++;
        return i;
    }

    /**
     * Returns a hash combining information of several keypoints.
     * @param points an array of key points for a particular slice of time. Must be at least length 4.
     * @return a long that is a hash representing each unique frequency domain.
     */
    @Override
    public long hash(long[] points) {
        long p0 = points[0];
        long p1 = points[1];
        long p2 = points[2];
        long p3 = points[3];

        return (p3 - (p3 % FUZ_FACTOR)) * 100000000 + (p2 - (p2 % FUZ_FACTOR))
                * 100000 + (p1 - (p1 % FUZ_FACTOR)) * 100
                + (p0 - (p0 % FUZ_FACTOR));
    }

}
