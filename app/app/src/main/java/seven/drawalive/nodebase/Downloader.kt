package seven.drawalive.nodebase

import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask

import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class Downloader(private val context: Context, private val callback: Runnable?) {
    private val progress: ProgressDialog

    class DownloadTask(private val downloader: Downloader) : AsyncTask<String, String, String>() {

        override fun doInBackground(vararg strings: String): String? {
            val url = strings[0]
            val outfile = strings[1]
            var download_stream: InputStream? = null
            var output_stream: OutputStream? = null
            var conn: HttpURLConnection? = null
            publishProgress("Starting ...")
            try {
                val urlobj = URL(url)
                conn = urlobj.openConnection() as HttpURLConnection
                if (conn.responseCode / 100 != 2) {
                    throw IOException("server error: " + conn.responseCode)
                }
                val file_len = conn.contentLength
                val buf = ByteArray(1024 * 1024)
                var read_len = 0
                var total_read_len = 0
                download_stream = conn.inputStream
                Storage.unlink(outfile)
                Storage.touch(outfile)
                output_stream = FileOutputStream(outfile)
                read_len = download_stream!!.read(buf)
                while (read_len >= 0) {
                    if (isCancelled) {
                        throw IOException("user cancelled")
                    }
                    total_read_len += read_len
                    output_stream.write(buf, 0, read_len)
                    var read_size = Storage.readableSize(total_read_len)
                    if (file_len > 0) {
                        read_size += " / " + Storage.readableSize(file_len)
                    }
                    publishProgress(read_size)
                    read_len = download_stream!!.read(buf)
                }
                output_stream.close()
                download_stream!!.close()
                publishProgress("Finishing ...")
            } catch (e: MalformedURLException) {
                e.printStackTrace()
                return e.toString()
            } catch (e: IOException) {
                e.printStackTrace()
                return e.toString()
            } finally {
                if (download_stream != null) try {
                    download_stream.close()
                } catch (e: IOException) {
                }

                if (output_stream != null) try {
                    output_stream.close()
                } catch (e: IOException) {
                }

                if (conn != null) conn.disconnect()
            }
            return null
        }

        override fun onPreExecute() {
            super.onPreExecute()
            downloader.progress.max = 100
            downloader.progress.progress = 0
            downloader.progress.show()
        }

        override fun onProgressUpdate(vararg data: String) {
            downloader.progress.setMessage(data[0])
        }

        override fun onPostExecute(result: String?) {
            downloader.progress.setMessage("do post actions ...")
            if (downloader.callback != null) {
                downloader.callback.run()
            }
            downloader.progress.dismiss()
            if (result == null) {
                Alarm.showToast(downloader.context, "Download successful")
            } else {
                Alarm.showToast(downloader.context, "Download failed: $result")
            }
        }
    }

    init {
        progress = ProgressDialog(context)
        progress.isIndeterminate = true
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progress.setCancelable(true)
    }

    fun act(title: String, url: String, outfile: String) {
        val task = DownloadTask(this)
        progress.setTitle(title)
        progress.setOnCancelListener { task.cancel(true) }
        task.execute(url, outfile)
    }
}
