package net.seven.nodebase

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Storage {

    private val READABLE_SIZE_UNIT = arrayOf("B", "KB", "MB", "GB", "TB")
    fun download(url: String, outfile: String): Boolean {
        var download_stream: InputStream? = null
        var output_stream: OutputStream? = null
        try {
            val urlobj = URL(url)
            val conn = urlobj.openConnection()
            // int file_len = conn.getContentLength();
            val buf = ByteArray(4096)
            var read_len = 0
            download_stream = conn.getInputStream()
            Storage.unlink(outfile)
            Storage.touch(outfile)
            output_stream = FileOutputStream(outfile)
            read_len = download_stream!!.read(buf)
            while (read_len >= 0) {
                output_stream.write(buf, 0, read_len)
                read_len = download_stream!!.read(buf)
            }
            output_stream.close()
            download_stream!!.close()
        } catch (e: MalformedURLException) {
        } catch (e: IOException) {
            return false
        } finally {
            if (download_stream != null) try {
                download_stream.close()
            } catch (e: IOException) {
            }

            if (output_stream != null) try {
                output_stream.close()
            } catch (e: IOException) {
            }

        }
        return true
    }

    fun copy(infile: String, outfile: String): Boolean {
        var in_stream: InputStream? = null
        var out_stream: OutputStream? = null
        try {
            in_stream = FileInputStream(infile)
            Storage.unlink(outfile)
            Storage.touch(outfile)
            out_stream = FileOutputStream(outfile)
            val buf = ByteArray(4096)
            var read_len = in_stream.read(buf)
            while (read_len >= 0) {
                out_stream.write(buf, 0, read_len)
                read_len = in_stream.read(buf)
            }
        } catch (e: FileNotFoundException) {
            return false
        } catch (e: IOException) {
            return false
        } finally {
            if (in_stream != null) try {
                in_stream.close()
            } catch (e: IOException) {
            }

            if (out_stream != null) try {
                out_stream.close()
            } catch (e: IOException) {
            }

        }
        return true
    }

    fun unlink(infile: String): Boolean {
        val file = File(infile)
        return if (file.exists()) file.delete() else false
    }

    fun touch(infile: String): Boolean {
        val file = File(infile)
        if (!file.exists()) try {
            file.createNewFile()
        } catch (e: IOException) {
        }

        return true
    }

    fun move(infile: String, outfile: String): Boolean {
        var r = Storage.copy(infile, outfile)
        if (r) {
            r = Storage.unlink(infile)
        } else {
            // rollback
            Storage.unlink(outfile)
        }
        return r
    }

    fun executablize(infile: String): Boolean {
        val file = File(infile)
        return file.setExecutable(true)
    }

    fun makeDirectory(path: String): Boolean {
        val dir = File(path)
        return if (dir.exists()) dir.isDirectory else dir.mkdirs()
    }

    fun read(infile: String): String? {
        var reader: FileInputStream? = null
        val file = File(infile)
        try {
            val buf = ByteArray(file.length().toInt())
            reader = FileInputStream(file)
            reader.read(buf)
            return buf.toString(Charset.defaultCharset())
        } catch (e: IOException) {
            return null
        } finally {
            if (reader != null) try {
                reader.close()
            } catch (e: Exception) {
            }

        }
    }

    fun write(text: String, outfile: String): Boolean {
        var writer: OutputStream? = null
        try {
            val buf = text.toByteArray()
            Storage.touch(outfile)
            writer = FileOutputStream(outfile)
            writer.write(buf)
        } catch (e: FileNotFoundException) {
            return false
        } catch (e: IOException) {
            return false
        } finally {
            if (writer != null) try {
                writer.close()
            } catch (e: Exception) {
            }

        }
        return true
    }

    fun listDirectories(path: String): Array<File>? {
        val filtered = ArrayList<File>()
        val dir = File(path)
        if (!dir.exists()) return null
        var list = dir.listFiles()
        for (f in list) {
            if (f.isDirectory) filtered.add(f)
        }
        return filtered.toTypedArray()
    }

    fun listFiles(path: String): Array<File>? {
        val filtered = ArrayList<File>()
        val dir = File(path)
        if (!dir.exists()) return null
        var list = dir.listFiles()
        for (f in list) {
            if (f.isFile) filtered.add(f)
        }
        return filtered.toTypedArray()
    }

    fun unzip(zipfile: String, target_dir: String): Array<File> {
        val unzipFiles = ArrayList<File>()
        try {
            val `in` = FileInputStream(zipfile)
            val zip = ZipInputStream(`in`)
            var entry: ZipEntry? = null
            entry = zip.nextEntry
            while (entry != null) {
                val target_filename = String.format("%s/%s", target_dir, entry!!.name)
                System.out.println(target_filename);
                if (entry!!.isDirectory) {
                    Storage.makeDirectory(target_filename)
                } else {
                    val file = File(target_filename)
                    val dir = file.getParentFile()
                    if (!dir.exists()) {
                        Storage.makeDirectory(dir.getAbsolutePath())
                    }
                    val out = FileOutputStream(target_filename)
                    val writer = BufferedOutputStream(out)
                    val buf = ByteArray(4096)
                    var count = zip.read(buf)
                    while (count != -1) {
                        writer.write(buf, 0, count)
                        count = zip.read(buf)
                    }
                    writer.close()
                    out.close()
                    zip.closeEntry()
                    unzipFiles.add(File(target_filename))
                }
                entry = zip.nextEntry
            }
            zip.close()
            `in`.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return unzipFiles.toTypedArray()
        } catch (e: IOException) {
            e.printStackTrace()
            return unzipFiles.toTypedArray()
        }

        return unzipFiles.toTypedArray()
    }

    fun zip(target_dir: String, zipfile: String): Boolean {
        val files = ArrayList<File>()
        val todos = ArrayList<File>()
        val dir = File(target_dir)
        if (!dir.exists()) return false
        todos.add(dir)
        while (todos.size > 0) {
          val cur = todos.removeAt(0);
          val list = cur.listFiles()
          for (f in list) {
            if (f.isDirectory) {
              todos.add(f)
            } else {
              files.add(f)
            }
          }
        }
        return zip(files.toTypedArray(), target_dir, zipfile)
    }

    fun zip(target_files: Array<File>, base_dir: String, zipfile: String): Boolean {
        val zipout = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipfile)));
        zipout.use { out ->
            for (file in target_files) {
                val filename = file.getAbsolutePath()
                var zipname = filename
                if (zipname.startsWith(base_dir)) {
                    zipname = zipname.substring(base_dir.length)
                }
                if (zipname.startsWith("/")) {
                    zipname = zipname.substring(1)
                }
                System.out.println(filename)
                FileInputStream(filename).use { fi ->
                    BufferedInputStream(fi).use { origin ->
                        val entry = ZipEntry(zipname)
                        out.putNextEntry(entry)
                        origin.copyTo(out, 1024)
                        zipout.closeEntry()
                    }
                }
            }
        }
        return true
    }

    fun exists(infile: String): Boolean {
        return File(infile).exists()
    }

    fun readableSize(size: Int): String {
        var index = 0
        val n = READABLE_SIZE_UNIT.size - 1
        var `val` = size.toDouble()
        while (`val` > 1024 && index < n) {
            index++
            `val` /= 1024.0
        }
        return String.format("%.2f %s", `val`, READABLE_SIZE_UNIT[index])
    }
}
