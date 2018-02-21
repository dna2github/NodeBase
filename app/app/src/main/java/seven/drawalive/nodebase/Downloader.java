package seven.drawalive.nodebase;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

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
         publishProgress("Starting ...");
         try {
            URL urlobj = new URL(url);
            URLConnection conn = urlobj.openConnection();
            int file_len = conn.getContentLength();
            byte[] buf = new byte[4096];
            int read_len = 0, total_read_len = 0;
            download_stream = conn.getInputStream();
            Storage.unlink(outfile);
            Storage.touch(outfile);
            output_stream = new FileOutputStream(outfile);
            while ((read_len = download_stream.read(buf)) >= 0) {
               if (isCancelled()) {
                  throw new IOException();
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
            return null;
         } catch (IOException e) {
            return null;
         } finally {
            if (download_stream != null) try {download_stream.close();} catch (IOException e) {}
            if (output_stream != null) try {output_stream.close();} catch (IOException e) {}
         }
         return outfile;
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
         if (result != null) {
            External.showToast(downloader.context,"Download successful");
         } else {
            External.showToast(downloader.context,"Download failed");
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
