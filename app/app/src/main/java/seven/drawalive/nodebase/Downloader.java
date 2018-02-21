package seven.drawalive.nodebase;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class Downloader {
   public static class DownloadTask extends AsyncTask<String, String, String> {
      public DownloadTask(Downloader downloader) {
         this.downloader = downloader;
      }

      @Override
      protected String doInBackground(String... strings) {
         String url = strings[0];
         String outfile = strings[1];
         InputStream download_stream = null;
         OutputStream output_stream = null;
         HttpURLConnection conn = null;
         publishProgress("Starting ...");
         try {
            URL urlobj = new URL(url);
            conn = (HttpURLConnection)urlobj.openConnection();
            if (conn.getResponseCode()/200 != 2) {
               throw new IOException("server error: " + conn.getResponseCode());
            }
            int file_len = conn.getContentLength();
            byte[] buf = new byte[1024*1024];
            int read_len = 0, total_read_len = 0;
            download_stream = conn.getInputStream();
            Storage.unlink(outfile);
            Storage.touch(outfile);
            output_stream = new FileOutputStream(outfile);
            while ((read_len = download_stream.read(buf)) >= 0) {
               if (isCancelled()) {
                  throw new IOException("user cancelled");
               }
               total_read_len += read_len;
               output_stream.write(buf, 0, read_len);
               String read_size = Storage.readableSize(total_read_len);
               if (file_len > 0) {
                  read_size += " / " + Storage.readableSize(file_len);
               }
               publishProgress(read_size);
            }
            output_stream.close();
            download_stream.close();
            publishProgress("Finishing ...");
         } catch (MalformedURLException e) {
            return e.toString();
         } catch (IOException e) {
            return e.toString();
         } finally {
            if (download_stream != null) try {download_stream.close();} catch (IOException e) {}
            if (output_stream != null) try {output_stream.close();} catch (IOException e) {}
            if (conn != null) conn.disconnect();
         }
         return null;
      }

      @Override
      protected void onPreExecute() {
         super.onPreExecute();
         downloader.progress.setMax(100);
         downloader.progress.setProgress(0);
         downloader.progress.show();
      }

      @Override
      protected void onProgressUpdate(String... data) {
         downloader.progress.setMessage(data[0]);
      }

      @Override
      protected void onPostExecute(String result) {
         if (downloader.callback != null) {
            downloader.callback.run();
         }
         downloader.progress.dismiss();
         if (result == null) {
            Alarm.showToast(downloader.context,"Download successful");
         } else {
            Alarm.showToast(downloader.context,"Download failed: " + result);
         }
      }

      private Downloader downloader;
   }

   public Downloader(Context context, Runnable callback) {
      this.context = context;
      progress = new ProgressDialog(context);
      progress.setIndeterminate(true);
      progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      progress.setCancelable(true);
      this.callback = callback;
   }

   public void act(String title, String url, String outfile) {
      final DownloadTask task = new DownloadTask(this);
      progress.setTitle(title);
      progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
         @Override
         public void onCancel(DialogInterface dialog) {
            task.cancel(true);
         }
      });
      task.execute(url, outfile);
   }

   private Context context;
   private Runnable callback;
   private ProgressDialog progress;
}
