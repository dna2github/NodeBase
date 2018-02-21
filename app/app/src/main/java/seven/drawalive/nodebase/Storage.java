package seven.drawalive.nodebase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

public class Storage {
   public static boolean download(String url, String outfile) {
      InputStream download_stream = null;
      OutputStream output_stream = null;
      try {
         URL urlobj = new URL(url);
         URLConnection conn = urlobj.openConnection();
         // int file_len = conn.getContentLength();
         byte[] buf = new byte[4096];
         int read_len = 0;
         download_stream = conn.getInputStream();
         Storage.unlink(outfile);
         Storage.touch(outfile);
         output_stream = new FileOutputStream(outfile);
         while ((read_len = download_stream.read(buf)) >= 0) {
            output_stream.write(buf, 0, read_len);
         }
         output_stream.close();
         download_stream.close();
      } catch (MalformedURLException e) {
      } catch (IOException e) {
         return false;
      } finally {
         if (download_stream != null) try {download_stream.close();} catch (IOException e) {}
         if (output_stream != null) try {output_stream.close();} catch (IOException e) {}
      }
      return true;
   }

   public static boolean copy(String infile, String outfile) {
      InputStream in_stream = null;
      OutputStream out_stream = null;
      try {
         in_stream = new FileInputStream(infile);
         Storage.unlink(outfile);
         Storage.touch(outfile);
         out_stream = new FileOutputStream(outfile);
         byte[] buf = new byte[4096];
         int read_len = 0;
         while ((read_len = in_stream.read(buf)) >= 0) {
            out_stream.write(buf, 0, read_len);
         }
      } catch (FileNotFoundException e) {
         return false;
      } catch (IOException e) {
         return false;
      } finally {
         if (in_stream != null) try {in_stream.close();} catch (IOException e) {}
         if (out_stream != null) try {out_stream.close();} catch (IOException e) {}
      }
      return true;
   }

   public static boolean unlink(String infile) {
      File file = new File(infile);
      if (file.exists()) return file.delete();
      return false;
   }

   public static boolean touch(String infile) {
      File file = new File(infile);
      if (!file.exists()) try { file.createNewFile(); } catch (IOException e) {}
      return true;
   }

   public static boolean move(String infile, String outfile) {
      boolean r = Storage.copy(infile, outfile);
      if (r) {
         r = Storage.unlink(infile);
      } else {
         // rollback
         Storage.unlink(outfile);
      }
      return r;
   }

   public static boolean executablize(String infile) {
      File file = new File(infile);
      return file.setExecutable(true);
   }

   public static boolean makeDirectory(String path) {
      File dir = new File(path);
      if (dir.exists()) return dir.isDirectory();
      return dir.mkdirs();
   }

   public static String read(String infile) {
      FileInputStream reader = null;
      File file = new File(infile);
      try {
         byte[] buf = new byte[(int) file.length()];
         reader = new FileInputStream(file);
         reader.read(buf);
         return new String(buf);
      } catch (IOException e) {
         return null;
      } finally {
         if (reader != null) try { reader.close(); } catch (Exception e) {}
      }
   }

   public static boolean write(String text, String outfile) {
      OutputStream writer = null;
      try {
         byte[] buf = text.getBytes();
         Storage.touch(outfile);
         writer = new FileOutputStream(outfile);
         writer.write(buf);
      } catch (FileNotFoundException e) {
         return false;
      } catch (IOException e) {
         return false;
      } finally {
         if (writer != null) try { writer.close(); } catch (Exception e) {}
      }
      return true;
   }

   public static File[] listDirectories(String path) {
      ArrayList<File> filtered = new ArrayList<>();
      File dir = new File(path);
      if (!dir.exists()) return null;
      File[] list = dir.listFiles();
      for (File f : list) {
         if (f.isDirectory()) filtered.add(f);
      }
      list = new File[filtered.size()];
      filtered.toArray(list);
      return list;
   }

   public static File[] listFiles(String path) {
      ArrayList<File> filtered = new ArrayList<>();
      File dir = new File(path);
      if (!dir.exists()) return null;
      File[] list = dir.listFiles();
      for (File f : list) {
         if (f.isFile()) filtered.add(f);
      }
      list = new File[filtered.size()];
      filtered.toArray(list);
      return list;
   }

   public static boolean exists(String infile) {
      return new File(infile).exists();
   }

   private static final String[] READABLE_SIZE_UNIT = new String[] {"B", "KB", "MB", "GB", "TB"};
   public static String readableSize(int size) {
      int index = 0, n = READABLE_SIZE_UNIT.length - 1;
      double val = size;
      while (val > 1024 && index < n) {
         index ++;
         val /= 1024.0;
      }
      return String.format("%.2f %s", val, READABLE_SIZE_UNIT[index]);
   }
}
