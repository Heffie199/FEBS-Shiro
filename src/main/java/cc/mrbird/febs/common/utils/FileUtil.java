package cc.mrbird.febs.common.utils;

import cc.mrbird.febs.common.entity.FebsConstant;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import org.springframework.util.FileSystemUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author MrBird
 */
@Slf4j
public abstract class FileUtil {

    private static final int BUFFER = 1024 * 8;

    /**
     * 压缩文件或目录
     *
     * @param fromPath 待压缩文件或路径
     * @param toPath   压缩文件，如 xx.zip
     */
    public static void compress(String fromPath, String toPath) throws IOException {
        File fromFile = new File(fromPath);
        File toFile = new File(toPath);
        if (!fromFile.exists()) {
            throw new FileNotFoundException(fromPath + "不存在！");
        }
        try (
                FileOutputStream outputStream = new FileOutputStream(toFile);
                CheckedOutputStream checkedOutputStream = new CheckedOutputStream(outputStream, new CRC32());
                ZipOutputStream zipOutputStream = new ZipOutputStream(checkedOutputStream)
        ) {
            String baseDir = "";
            compress(fromFile, zipOutputStream, baseDir);
        }
    }

    /**
     * 文件下载
     *
     * @param filePath 待下载文件路径
     * @param fileName 下载文件名称
     * @param delete   下载后是否删除源文件
     * @param response HttpServletResponse
     * @throws Exception Exception
     */
    public static void download(String filePath, String fileName, Boolean delete, HttpServletResponse response) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("文件未找到");
        }

        String fileType = getFileType(file);
        if (!fileTypeIsValid(fileType)) {
            throw new Exception("暂不支持该类型文件下载");
        }
        response.setHeader("Content-Disposition", "attachment;fileName=" + java.net.URLEncoder.encode(fileName, "utf-8"));
        response.setContentType("multipart/form-data");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (InputStream inputStream = new FileInputStream(file); OutputStream os = response.getOutputStream()) {
            byte[] b = new byte[2048];
            int length;
            while ((length = inputStream.read(b)) > 0) {
                os.write(b, 0, length);
            }
        } finally {
            if (delete) {
                FileSystemUtils.deleteRecursively(file);
            }
        }
    }

    /**
     * 获取文件类型
     *
     * @param file 文件
     * @return 文件类型
     * @throws Exception Exception
     */
    private static String getFileType(File file) throws Exception {
        Preconditions.checkNotNull(file);
        if (file.isDirectory()) {
            throw new Exception("file不是文件");
        }
        String fileName = file.getName();
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }


    /**
     * 校验文件类型是否是允许下载的类型
     * （出于安全考虑：https://github.com/wuyouzhuguli/FEBS-Shiro/issues/40）
     *
     * @param fileType fileType
     * @return Boolean
     */
    private static Boolean fileTypeIsValid(String fileType) {
        Preconditions.checkNotNull(fileType);
        fileType = StringUtils.lowerCase(fileType);
        return ArrayUtils.contains(FebsConstant.VALID_FILE_TYPE, fileType);
    }

    private static void compress(File file, ZipOutputStream zipOut, String baseDir) throws IOException {
        if (file.isDirectory()) {
            compressDirectory(file, zipOut, baseDir);
        } else {
            compressFile(file, zipOut, baseDir);
        }
    }

    private static void compressDirectory(File dir, ZipOutputStream zipOut, String baseDir) throws IOException {
        File[] files = dir.listFiles();
        if (files != null && ArrayUtils.isNotEmpty(files)) {
            for (File file : files) {
                compress(file, zipOut, baseDir + dir.getName() + File.separator);
            }
        }
    }

    /**
     * 删除文件或者文件夹<br>
     * 注意：删除文件夹时不会判断文件夹是否为空，如果不空则递归删除子文件或文件夹<br>
     * 某个文件删除失败会终止删除操作
     *
     * <p>
     * 从5.7.6开始，删除文件使用{@link Files#delete(Path)}代替 {@link File#delete()}<br>
     * 因为前者遇到文件被占用等原因时，抛出异常，而非返回false，异常会指明具体的失败原因。
     * </p>
     *
     * @param file 文件对象
     * @return 成功与否
     * @throws File IO异常
     * @see Files#delete(Path)
     */
    public static boolean del(File file) throws Exception {
        if (file == null || false == file.exists()) {
            // 如果文件不存在或已被删除，此处返回true表示删除成功
            return true;
        }

        if (file.isDirectory()) {
            // 清空目录下所有文件和目录
            boolean isOk = clean(file);
            if (false == isOk) {
                return false;
            }
        }

        // 删除文件或清空后的目录
        final Path path = file.toPath();
        try {
            delFile(path);
        } catch (DirectoryNotEmptyException e) {
            // 遍历清空目录没有成功，此时补充删除一次（可能存在部分软链）
//            del(path);
        } catch (IOException e) {
            throw new Exception(e);
        }

        return true;
    }
    /**
     * 删除文件或空目录，不追踪软链
     *
     * @param path 文件对象
     * @throws IOException IO异常
     * @since 5.7.7
     */
    protected static void delFile(Path path) throws IOException {
        try {
            Files.delete(path);
        } catch (AccessDeniedException e) {
            // 可能遇到只读文件，无法删除.使用 file 方法删除
            if (false == path.toFile().delete()) {
                throw e;
            }
        }
    }
    /**
     * 清空文件夹<br>
     * 注意：清空文件夹时不会判断文件夹是否为空，如果不空则递归删除子文件或文件夹<br>
     * 某个文件删除失败会终止删除操作
     *
     * @param directory 文件夹
     * @return 成功与否
     * @throws Exception IO异常
     * @since 3.0.6
     */
    public static boolean clean(File directory) throws Exception {
        if (directory == null || directory.exists() == false || false == directory.isDirectory()) {
            return true;
        }

        final File[] files = directory.listFiles();
        if (null != files) {
            for (File childFile : files) {
                if (false == del(childFile)) {
                    // 删除一个出错则本次删除任务失败
                    return false;
                }
            }
        }
        return true;
    }

    private static void compressFile(File file, ZipOutputStream zipOut, String baseDir) throws IOException {
        if (!file.exists()) {
            return;
        }
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            ZipEntry entry = new ZipEntry(baseDir + file.getName());
            zipOut.putNextEntry(entry);
            int count;
            byte[] data = new byte[BUFFER];
            while ((count = bis.read(data, 0, BUFFER)) != -1) {
                zipOut.write(data, 0, count);
            }
        }
    }


        // 通过url下载图片
        public static void downloadPicture(String urlList, String path) {
            URL url = null;
            try {
                url = new URL(urlList);
                DataInputStream dataInputStream = new DataInputStream(url.openStream());  // 文件读取

                // 如果目录不存在就创建目录
                File file = new File(path);
                if (!file.getParentFile().exists()) {
                    try {
                        file.getParentFile().mkdirs();
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                FileOutputStream fileOutputStream = new FileOutputStream(file); // 文件写出
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;

                while ((length = dataInputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                fileOutputStream.write(outputStream.toByteArray());

                // 连接关闭
                dataInputStream.close();
                fileOutputStream.close();
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    /**
     * 通过URL下载文件到指定路径
     *
     * @param url      URL
     * @param filePath 指定路径
     * @param headers  HTTP头
     * @return
     * @throws IOException
     *//*

    public static File download(String url, String filePath, NameValuePair... headers) throws IOException {
        File file = new File(filePath);

        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(url);
        if (!ABTextUtil.isEmpty(headers)) {
            for (NameValuePair header : headers) {
                request.addHeader(header.getName(), header.getValue());
            }
        }
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                is = response.getEntity().getContent();
                fos = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.flush();
            }
        } catch (IOException e) {

        } finally {
            ABIOUtil.closeIO(is, fos);
        }
        return file;
    }*/

}
