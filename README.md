# java-video-downloader-util
Transport Stream (TS) video downloader utility.
Applicable for downloading *.ts video files from websites.


## System Requirements
1. Java version 8+ --> https://www.oracle.com/java/technologies/javase-downloads.html
2. Maven --> https://maven.apache.org/install.html
3. Install FFmpeg --> https://ffmpeg.org/download.html
   * How to install FFmpeg and add to system path --> https://www.wikihow.com/Install-FFmpeg-on-Windows


## Usage:

#### * The downloaded output video can be found under:  `/<_your-system_>/java-video-downloader-util/VideoOutputDirectory/Full_Video.ts`
#### * The website's HAR log file can be found under:   `/<_your-system_>/java-video-downloader-util/HttpArchiveOutput/trace-website-http-requests.har`


### IDE:
1. Run as mvn project
2. Pass the website url as the 1st and only cmd line args `https://website-with-video.com`

### Command Line Maven:
1. `mvn clean compile`
2. `mvn exec:java -Dexec.mainClass="com.video.downloader.MainVideoDownloader" -Dexec.args="https://website-with-video.com"`

### EXAMPLE USAGE
* To Download video from _https://www.nbcnews.com/nightly-news/video/russian-military-harassing-u-s-civilian-fishing-boats-near-alaska-exclusive-110692933767_
* Run `mvn exec:java -Dexec.mainClass="com.video.downloader.MainVideoDownloader" -Dexec.args="https://www.nbcnews.com/nightly-news/video/russian-military-harassing-u-s-civilian-fishing-boats-near-alaska-exclusive-110692933767"`
* Final output video should appear under `/<_your-system_>/java-video-downloader-util/VideoOutputDirectory/Full_Video.ts`

## Utility Execution steps
1. Search and find m3u8 file http requests in Website
   (Uses Selenium and Browsermob proxy to run Chrome and capture http requests. Http Archive HAR file is created in `/<your-system>/java-video-downloader-util/HttpArchiveOutput/trace-website-http-requests.har`
2. Identify M3U8 master playlist file and pick out the best quality bit rate variant m3u8
3. Download/Read m3u8 text file which contains all path references for video *.ts file urls
4. Multi-thread video *.ts file downloads
   * Utility implements retry logic for any unsuccessful *.ts file downloads
5. Download and write each individual *.ts file to `/<your-system>/java-video-downloader-util/VideoOutputDirectory/.`
6. Run/Execute **ffmpeg** command to concat all *.ts files into a single output Full_Video.ts file under `/<your-system>/java-video-downloader-util/VideoOutputDirectory/Full_Video.ts`

## References
* https://developer.apple.com/documentation/http_live_streaming/example_playlists_for_http_live_streaming/creating_a_master_playlist
* https://en.wikipedia.org/wiki/M3U

## Considerations
* This utility has only been tested on a Windows 10 OS machine. Functionality is not guaranteed on another platform