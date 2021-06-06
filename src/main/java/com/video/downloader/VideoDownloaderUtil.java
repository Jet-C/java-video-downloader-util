package com.video.downloader;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownloaderUtil {

    public static AtomicInteger downloadCounter = new AtomicInteger(1);
    public static AtomicInteger totalDownloadCounter = new AtomicInteger(0);
    public static int filesToDownloadCount;

    public void downloaderUtil(String m3u8FileURL) throws IOException {

        // Download m3u8 description file and return a list of its .ts url contents
        List<String> m3u8_tsFiles_List = this.processM3U8urlFile(m3u8FileURL);
        if (m3u8_tsFiles_List.isEmpty()) {
            System.err.println("ERROR!!!ERROR!!! - No TS files... Exiting >>>" + m3u8FileURL);
            return;
        } else {
            // Check if the extracted urls are relative or absolute paths
            String tsVideoUrl = m3u8_tsFiles_List.get(0);
            if (!tsVideoUrl.startsWith("http:") && !tsVideoUrl.startsWith("https:")) {
                System.out.println("WARN! WARN! The following file location is not an absolute path URL!\n" +
                        " Current relative path = " + tsVideoUrl + " Absolute path will be based on current on the M3U8 file path: " + m3u8FileURL);

                List<String> absolute_m3u8_tsFiles_List = new ArrayList<>();
                for (String relativeTSurls : m3u8_tsFiles_List) {
                    // Using M3U8 url to construct absolute paths for ts file urls
                    String absoluteTS = getAbsolutePath(m3u8FileURL, relativeTSurls);
                    absolute_m3u8_tsFiles_List.add(absoluteTS);
                }
                System.out.println("New absolute file path url" + absolute_m3u8_tsFiles_List.get(0));
                m3u8_tsFiles_List = absolute_m3u8_tsFiles_List;
            }
        }

        filesToDownloadCount = m3u8_tsFiles_List.size();

        // Video files will be downloaded to  <projectDirectory>/java-video-downloader-util/VideoOutputDirectory/.
        String download = Paths.get(".").toAbsolutePath().toUri().normalize().getRawPath() + "VideoOutputDirectory/";
        String downloadDirectory;
        if (download.startsWith("\\") || download.startsWith("/")) {
            downloadDirectory = download.substring(1);
        } else {
            downloadDirectory = download;
        }

        System.out.println("\nWARNING!! WARNING!! Will overwrite existing Full_Video.ts\n");
        System.out.println("Download videos to directory >> " + downloadDirectory + "\n");

        // The ffmpeg tool requires the input string formatted as:  "filename_1|filename_2|filename_3|filename_n"
        // TsFile names must remain in the same natural order present in .m3u8 file
        // WARNING: Maximum command length is 8191 characters for a Windows OS Command prompt (Cmd.exe)
        // Break up command string in multiple parts if command were to exceed char limit
        Queue<StringBuilder> ffmpegCommandBuilderList = new LinkedList<>();
        StringBuilder tsFileNamesBuilder = new StringBuilder();
        Map<String, String> tsLinkVsPathHashmap = new HashMap<>();

        for (String tsLink : m3u8_tsFiles_List) {
            Pattern p = Pattern.compile("[^\\/]+(?=\\.ts).ts");   // the pattern to search for
            Matcher m = p.matcher(tsLink);
            String videoName = null;
            if (m.find()) {
                videoName = m.group(0);
            }
            String tsFilePath = downloadDirectory + videoName;
            tsLinkVsPathHashmap.put(tsLink, tsFilePath);

            tsFileNamesBuilder.append(tsFilePath).append("|");
            if (tsFileNamesBuilder.length() > 7900) { // Don't exceed 8191 cmd limit
                System.out.println("WARNING: Exceeded character length >> " + tsFileNamesBuilder.length());
                ffmpegCommandBuilderList.add(tsFileNamesBuilder);
                tsFileNamesBuilder = new StringBuilder();
            }
            System.out.println("Ts file path appended: " + tsFilePath);
        }
        // Add last or first part of builder depending on builder string length
        ffmpegCommandBuilderList.add(tsFileNamesBuilder);

        System.out.println("Total number of command ts pieces " + ffmpegCommandBuilderList.size());
        // remove the last trailing pipe "|" character in the input string
        for (StringBuilder builder : ffmpegCommandBuilderList) {
            builder.deleteCharAt(builder.length() - 1);
        }

        // Download and save all 'ts' files. 'NULL' is returned if ALL files were successfully downloaded.
        // Else, failed files will be retried.
        List<ResultVO> retryDownloadsList = downloadAndStoreFiles(m3u8_tsFiles_List, tsLinkVsPathHashmap, null);

        // Failed files will be retired 3 times.
        if (CollectionUtils.isEmpty(retryDownloadsList)) {
            System.out.println("All files successfully downloaded no need to retry any! :)");
        } else {
            System.err.println("ERROR!!! ERROR!!! Some files were NOT successfully downloaded. Will retry for " + retryDownloadsList.size() + " file(s)");
            List<DownloaderTask> retryTaskList = getDownloaderTaskRetryList(retryDownloadsList);
            int retryCounter = 0;
            do {
                retryDownloadsList = downloadAndStoreFiles(m3u8_tsFiles_List, null, retryTaskList);
                if (!CollectionUtils.isEmpty(retryDownloadsList)) {
                    retryTaskList = getDownloaderTaskRetryList(retryDownloadsList);
                }
                retryCounter++;
            } while ((!CollectionUtils.isEmpty(retryDownloadsList)) && retryCounter != 3);

            if (retryCounter == 3) {
                System.out.println("ERROR!!! ERROR!!! ERROR!!!! - After more than 3 attempts some files did NOT successfully download. \n" +
                        "ERROR!!! ERROR!!! ERROR!!!! - Your video may NOT yield the expected result or ffmpeg tool can fail");
            }
        }

        String fullVideo = null;

        // Prepare and execute ts video concatenation
        int pieceCount = 0;
        while (!ffmpegCommandBuilderList.isEmpty()) {
            String outputFilePath = downloadDirectory + "piece_" + pieceCount + ".ts";

            if (ffmpegCommandBuilderList.size() == 1) {
                outputFilePath = downloadDirectory + "Full_Video.ts";
                fullVideo = outputFilePath;
                System.out.println("Full output video name = " + outputFilePath);
            }

            executeFFMPEG(outputFilePath, ffmpegCommandBuilderList.poll().toString());

            if (null != ffmpegCommandBuilderList.peek()) {
                StringBuilder nextCommandStr = ffmpegCommandBuilderList.peek();
                String tmpOutputFilePath = outputFilePath + "|";
                nextCommandStr.insert(0, tmpOutputFilePath);
                System.out.println("Modified command next iteration to be >> " + nextCommandStr.toString());
                pieceCount++;
            }

        }


        System.out.println("\n================== Video download finished for " + m3u8FileURL + " ==================");
        System.out.println("\n================== Full Video @ " + fullVideo + " ==================");
        // reset counter
        totalDownloadCounter.getAndAdd(downloadCounter.decrementAndGet());
        downloadCounter.set(1);
    }

    private void executeFFMPEG(String outputFilePath, String ffmpegInputFileNames) {
        System.out.println("\nExecuting ffmpeg with output path = " + outputFilePath + "\n");
        System.out.println("Input ffmpegInputFileNames character length >> " + ffmpegInputFileNames.length());
        // Build ffmpeg tool command and execute
        // ffmpeg will concat all our *.ts files and produce a single output video file
        ProcessBuilder processBuilder = new ProcessBuilder();
        String commandStr = "ffmpeg -i \"concat:" + ffmpegInputFileNames + "\" -c copy " + outputFilePath;
        // Run this on Windows, cmd, /c = terminate after this run
        processBuilder.command("cmd.exe", "/c", commandStr);
        processBuilder.redirectErrorStream(true);

        try {
            System.out.println("Executing command - " + commandStr);
            Process process = processBuilder.start();

            // Let's read and print the ffmpeg's output
            BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while (true) {
                line = r.readLine();
                if (line == null) {
                    break;
                }
                System.out.println(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * Download *.m3u8 file.
     * Find and get all *.ts file names in m3u8 file (These should be listed in sequential order)
     * Return list of *.ts file urls
     */
    public List<String> processM3U8urlFile(String m3u8Url) throws MalformedURLException {
        URL url = new URL(m3u8Url);

        List<String> m3u8_List = new ArrayList<>();

        try (InputStream is = url.openStream()) {
            String m3u8File = IOUtils.toString(is, StandardCharsets.UTF_8);
            // Find and get all *.ts file names in m3u8 file
            Matcher m = Pattern.compile(".*\\.ts.*", Pattern.MULTILINE).matcher(m3u8File);
            boolean isRelativePath = false;
            while (m.find()) {
                String tsVideoUrl = m.group();
                m3u8_List.add(tsVideoUrl);
            }
        } catch (IOException e) {
            System.err.printf("\nERROR!!!ERROR!!! Failed while reading bytes from %s: %s \n", url.toExternalForm(), e.getMessage());
            e.printStackTrace();
        }
        int m3u8_list_size = m3u8_List.size();
        System.out.println("Total ts files to be downloaded " + m3u8_list_size);

        return m3u8_List;
    }

    /*
     * Divide and conquer the video file downloads
     * Method also handles download retries if the 'retryTaskList' is not empty/null
     */
    public List<ResultVO> downloadAndStoreFiles(List<String> m3u8_List, Map<String, String> linkVsFilePath, List<DownloaderTask> retryTaskList) {

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<DownloaderTask> taskList;

        // Check if this is a retry download task
        if (!CollectionUtils.isEmpty(retryTaskList)) {
            taskList = retryTaskList;
        } else {
            taskList = new ArrayList<>();
            for (String tsUrlLink : m3u8_List) {
                String tsFilePath = linkVsFilePath.get(tsUrlLink);
                DownloaderTask downloaderTask = new DownloaderTask(tsUrlLink, tsFilePath);
                taskList.add(downloaderTask);
            }
        }

        // Execute all downloader tasks and get reference to Future objects
        List<Future<ResultVO>> resultList = null;
        try {
            resultList = executor.invokeAll(taskList);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        executor.shutdown();

        List<ResultVO> failedResultsList = new ArrayList<>();

        for (Future<ResultVO> future : resultList) {
            try {
                ResultVO result = future.get();
                if (null != result) {
                    System.out.println("Failed file details: " + result);
                    failedResultsList.add(result);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        if (CollectionUtils.isEmpty(failedResultsList))
            return null;

        System.out.println("===================== A total of " + failedResultsList.size() + " failed to download =====================");
        return failedResultsList;
    }

    /*
     * Create and get the Retry tasks for previously unsuccessful downloads
     */
    private List<DownloaderTask> getDownloaderTaskRetryList(List<ResultVO> retryDownloadsList) {
        System.out.println("\n===================== Retrying the following file links =====================");

        int fileCounter = 1;
        List<DownloaderTask> retryTaskList = new ArrayList<>();
        for (ResultVO resultVO : retryDownloadsList) {

            System.out.println(fileCounter + ". " + resultVO.getTsFileUrl());

            DownloaderTask downloaderTask = new DownloaderTask(resultVO.getTsFileUrl(), resultVO.getTsFilePath());
            retryTaskList.add(downloaderTask);
            fileCounter++;
        }
        return retryTaskList;
    }

    /*
     * Master/Playlist files can be identified if they contain the "EXT-X-STREAM-INF" tag
     */
    public String findMasterM3U(List<String> listOfHAR_m3u8Files) {
        System.out.println("\n Inspecting list of HAR m3u8 files to identify mater playlist");

        int counter = 0;
        for (String m3uFileUrl : listOfHAR_m3u8Files) {
            URL url;
            try {
                url = new URL(m3uFileUrl);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            }

            try (InputStream is = url.openStream()) {
                String playlistFileIO = IOUtils.toString(is, StandardCharsets.UTF_8);
                System.out.println("\n" + counter + ". Printing content of m3u8 file\n" + playlistFileIO);

                if (playlistFileIO.contains("EXT-X-STREAM-INF")) {
                    System.out.println("FOUND Master/Playlist m3u8 url: " + m3uFileUrl);
                    return m3uFileUrl;
                }

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            counter++;
        }

        System.out.println("WARNING! No m3u8 master playlist found!");
        return null;
    }

    /*
     * Multiple M3U8 file variants exist so find the highest quality one.
     */
    public String getM3U8variantWithHighestBitrate(String masterPlaylistUrl) {
        System.out.println("\nFinding highest bitrate in Master/Playlist file URL >>> " + masterPlaylistUrl);

        URL url;
        try {
            url = new URL(masterPlaylistUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }

        String highestBitRateM3U8url = null;
        try (InputStream is = url.openStream()) {
            String playlistFileIO = IOUtils.toString(is, StandardCharsets.UTF_8);
            System.out.println("Printing content of master playlist file >>>\n" + playlistFileIO);
            // Find all bit rate variants
            Matcher bitrateMatcher = Pattern.compile("[^AVERAGE-]BANDWIDTH=([0-9]+)", Pattern.MULTILINE).matcher(playlistFileIO);
            // Find and get all *.m3u8 file paths
            Matcher m3u8Matcher = Pattern.compile("^.*\\.m3u8.*", Pattern.MULTILINE).matcher(playlistFileIO);
            int highestBitRate = 0;
            int highestBitRateIdx = 0;
            int idxCounter = 0;
            System.out.println("Finding highest bitrate...");
            while (bitrateMatcher.find()) {
                int tempBitRate = Integer.parseInt(bitrateMatcher.group(1));
                System.out.println("tempBitRate - " + tempBitRate);
                if (highestBitRate < tempBitRate) {
                    highestBitRate = tempBitRate;
                    highestBitRateIdx = idxCounter;
                    System.out.println("New highestBitRate - " + highestBitRate + " at index - " + highestBitRateIdx);
                }
                idxCounter++;
            }
            List<String> m3u8MatcherList = new ArrayList<>();
            while (m3u8Matcher.find()) {
                System.out.println("m3u8Matcher - " + m3u8Matcher.group());
                m3u8MatcherList.add(m3u8Matcher.group());
            }
            highestBitRateM3U8url = m3u8MatcherList.get(highestBitRateIdx);
            System.out.println("Highest bit rate m3u8 match - " + highestBitRateM3U8url);
        } catch (IOException e) {
            System.err.printf("\nERROR!!!ERROR!!! Failed while reading bytes from %s: %s \n", url.toExternalForm(), e.getMessage());
            e.printStackTrace();
        }

        if (!highestBitRateM3U8url.startsWith("http:") && !highestBitRateM3U8url.startsWith("https:")) {
            System.out.println("WARN! WARN! The following file location is not an absolute path URL!\n" +
                    " Current relative path = " + highestBitRateM3U8url + " Absolute path will be based on current on the M3U8 file path: " + masterPlaylistUrl);
            // Using M3U8 url to construct absolute paths for ts file urls
            highestBitRateM3U8url = getAbsolutePath(masterPlaylistUrl, highestBitRateM3U8url);
            System.out.println("New absolute file path url" + highestBitRateM3U8url);
        }

        return highestBitRateM3U8url;
    }


    public String getAbsolutePath(String baseAbsolutePath, String relativePath) {
        int idx = baseAbsolutePath.lastIndexOf("/");
        String base = baseAbsolutePath.substring(0, idx + 1);
        String absolutePath = base + relativePath;
        return absolutePath;
    }

}
