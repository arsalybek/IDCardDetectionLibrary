package kz.kbtu.objectdetectionlibrary;

import android.content.Context;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileHelper {

    public static String writeImageFileToDisk(Context context, byte[] data) {
        try {
            File file = createImageFile(context);
            if (file == null) return null;
            OutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(file);
                outputStream.write(data);
                outputStream.flush();
                return file.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    static File createImageFile(Context context) {
        return createFile(context, "jpg", true);
    }

    public static File createFile(Context context, String extension, boolean isCache) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = extension.toUpperCase() + "_" + timeStamp + "_";
            File storageDir = isCache ? context.getCacheDir() : context.getDir("wisdom", Context.MODE_PRIVATE);
            return File.createTempFile(imageFileName, "." + extension.toLowerCase(), storageDir);
//            return new File(storageDir,imageFileName);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "fdfddfdf", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

}
