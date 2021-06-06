package com.video.downloader;

public class ResultVO {
    private String tsFileUrl;
    private String tsFilePath;

    public ResultVO(String tsFileUrl, String tsFilePath) {
        this.tsFileUrl = tsFileUrl;
        this.tsFilePath = tsFilePath;
    }

    public String getTsFileUrl() {
        return tsFileUrl;
    }

    public void setTsFileUrl(String tsFileUrl) {
        this.tsFileUrl = tsFileUrl;
    }

    public String getTsFilePath() {
        return tsFilePath;
    }

    public void setTsFilePath(String tsFilePath) {
        this.tsFilePath = tsFilePath;
    }

    @Override
    public String toString() {
        return "ResultVO{" +
                "tsFileUrl='" + tsFileUrl + '\'' +
                ", tsFilePath='" + tsFilePath + '\'' +
                '}';
    }
}
