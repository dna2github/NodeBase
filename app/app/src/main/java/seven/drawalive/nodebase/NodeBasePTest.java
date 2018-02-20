package seven.drawalive.nodebase;

import android.content.Context;
import android.os.Environment;

import java.io.File;

public class NodeBasePTest {
    public NodeBasePTest(Context context) {
        System.out.println(Environment.getExternalStorageDirectory());
        try {
            File f = new File(Environment.getExternalStorageDirectory(), "test_write");
            f.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
