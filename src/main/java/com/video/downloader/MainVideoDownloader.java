package com.video.downloader;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;

// Example ts video: https://www.nbcnews.com/nightly-news/video/russian-military-harassing-u-s-civilian-fishing-boats-near-alaska-exclusive-110692933767
public class MainVideoDownloader {

    public static void main(String[] args) {

        VideoDownloaderUtil videoDownloaderUtil = new VideoDownloaderUtil();

        System.out.println("\n===================== Video Downloader Started =====================");

        String directM3U8fileURL;

        if (args.length == 0 || args[0].length() <= 5) {

            System.err.println("Missing or invalid parameters \n" +
                    "Please enter a valid website url or the direct video m3u8 file url\n" +
                    "Sample m3u8 file url: https://www.some-website.com/with/a/video/chunklist_w98765432_b1087284.m3u8");

            System.err.println("If the website URL is provided, this script will ATTEMPT to find the master playlist m3u8 url request for the video stream\n" +
                    "If no http request for an m3u8 file is found, then this script will provide an HAR file for tracing http requests done by the website.");
            return;
        }

        System.out.println("Website args --> " + args[0]);

        // Direct m3u8 file was provided
        if (args[0].contains(".m3u8")) {
            directM3U8fileURL = args[0];
        }
        // Generic website URL was provided. Now attempting to find m3u8 file requests...
        else {
            SeleniumBMPInterceptor seleniumBMPInterceptor = new SeleniumBMPInterceptor();
            List<String> listOfHAR_m3uFiles = seleniumBMPInterceptor.retrieveM3U8requestFiles(args[0]);
            if (listOfHAR_m3uFiles.isEmpty()) {
                System.err.println("\nERROR! No http requests for m3u8 files were found while searching website --> " + args[0] +
                        "\nPlease provide the direct m3u8 URL.");
                return;
            } else {
                /*
                 * It is possible multiple *.m3u8 URLs were found including a "master m3u8 playlist" file descriptor
                 * Multiple *.m3u8 files likely represent bandwidth(bitrate) variants of the same video file.
                 * See --> https://developer.apple.com/documentation/http_live_streaming/example_playlists_for_http_live_streaming/creating_a_master_playlist
                 *
                 * Master/Playlist files are identified the "EXT-X-STREAM-INF" tag they contain
                 * The variant with the highest bitrate is generally the better quality video.
                 *         Example playlist.m3u8
                 *      #EXT-X-STREAM-INF:BANDWIDTH=152184
                 *      chunklist_w1961872646_b152184.m3u8
                 *      #EXT-X-STREAM-INF:BANDWIDTH=252408
                 *      chunklist_w1961872646_b252408.m3u8
                 */
                String masterM3U = videoDownloaderUtil.findMasterM3U(listOfHAR_m3uFiles);
                if (!StringUtils.isEmpty(masterM3U)) {
                    // Find and get the best quality (highest bitrate) variant in the master file
                    String highestBitRateVariant = videoDownloaderUtil.getM3U8variantWithHighestBitrate(masterM3U);
                    System.out.println("FOUND HIGHEST BIT RATE URL = " + highestBitRateVariant);
                    directM3U8fileURL = highestBitRateVariant;
                } else {
                    directM3U8fileURL = listOfHAR_m3uFiles.get(0);
                    System.out.println("WARNING!! WARNING!! Did not find a master playlist\n" +
                            "The first m3u8 url file found will be used to attempt video download --> " + directM3U8fileURL);
                }
            }
        }

        if (!StringUtils.isEmpty(directM3U8fileURL)) {
            System.out.println("\n=============== Starting video downloader util for m3u8 file --> " + directM3U8fileURL + "===============\n");
            try {
                videoDownloaderUtil.downloaderUtil(directM3U8fileURL);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("ERROR!!!ERROR!!! No M3U8 file url was provide/found");
            return;
        }

        System.out.println("TOTAL FILES DOWNLOADED: " + VideoDownloaderUtil.totalDownloadCounter.get());
        System.out.println("\n================== Video downloader concluded ==================");
    }

}