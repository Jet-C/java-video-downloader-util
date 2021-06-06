import org.junit.Assert;
import org.junit.Test;

public class Tests {

    @Test
    public void test() {
        System.out.println("Test");
    }

    @Test
    public void buildAbsolutePath() {
        String baseAbsolutePath = "http://xxxxxxxxx/s3/_definst_/smil:cor/1306/resolutions/resolutions.smil/playlist.m3u8";
        String relativePath = "chunklist_w2126937063_b1882996.m3u8";

        int idx = baseAbsolutePath.lastIndexOf("/");
        String base = baseAbsolutePath.substring(0, idx+1);
        String absolutePath = base + relativePath;

        String expected = "http://xxxxxxxxx/s3/_definst_/smil:cor/1306/resolutions/resolutions.smil/chunklist_w2126937063_b1882996.m3u8";

        System.out.println(absolutePath);
        Assert.assertEquals(expected, absolutePath);
    }
}