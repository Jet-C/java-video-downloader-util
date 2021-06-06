package com.video.downloader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

/*
 * Downloader Tasks to execute.
 * Opens HttpConnection and writes download to files.
 */
class DownloaderTask implements Callable<ResultVO> {

    private final String tsFileUrl;  // http call to file resource on website
    private final String tsFilePath; // path to save the video file on your computer

    public DownloaderTask(String tsFileUrl, String tsFilePath) {
        this.tsFileUrl = tsFileUrl;
        this.tsFilePath = tsFilePath;
    }

    @Override
    public ResultVO call() throws Exception {

        System.out.println("Begin downloading>>> " + tsFileUrl + "\n Downloading on thread - " + Thread.currentThread().getName());

        InputStream input = null;
        HttpURLConnection connection;
        boolean retry_downloadRequired = false;
        try {
            URL tsUrl = new URL(tsFileUrl);
            connection = (HttpURLConnection) tsUrl.openConnection();
            connection.connect();

            long urlFileLength = connection.getContentLength();
            input = connection.getInputStream();

            byte[] m3u8Bytes = IOUtils.toByteArray(input);
            long downloadedByteLength = m3u8Bytes.length;

            FileUtils.writeByteArrayToFile(new File(tsFilePath), m3u8Bytes);

            File file = new File(tsFilePath);
            long savedFileLength = file.length();

            System.out.println(tsFileUrl);
            System.out.println("urlFileLength: " + urlFileLength + " downloadedByteLength: " + downloadedByteLength + " savedFileLength: " + savedFileLength);

            // sanity check. Did my file truly download and store completely?? - If not lets retry the download
            if (urlFileLength != downloadedByteLength || urlFileLength != savedFileLength) {
                System.out.println("ERROR! Mismatching byte lengths! urlFileLength: " + urlFileLength + " downloadedByteLength: " + downloadedByteLength + " savedFileLength: " + savedFileLength);
                System.out.println("Will retry download for " + tsFileUrl);
                retry_downloadRequired = true;
            }

        } catch (Exception ex) {
            System.out.println("Error occurred");
        } finally {
            if (input != null) {
                input.close();
            }
        }

        // Return only Links (ResultVOs) that resulted in failed downloads
        if (retry_downloadRequired) {
            return new ResultVO(tsFileUrl, tsFilePath);
        } else {
            System.out.println("Successfully downloaded file: " + tsFilePath);
            System.out.println(VideoDownloaderUtil.downloadCounter.getAndIncrement() + " OUT OF " + VideoDownloaderUtil.filesToDownloadCount + " DOWNLOADED");
            return null;
        }
    }
}

