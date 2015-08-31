/**
 * @author By_syk
 */

package com.by_syk.osbuild.util;

import com.by_syk.osbuild.R;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.FileFilter;
import android.os.StatFs;
import android.os.Build;
import java.util.Locale;
import java.io.PrintWriter;
import java.util.List;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import android.text.TextUtils;
import android.content.pm.ApplicationInfo;
import java.util.ArrayList;
import java.util.Collections;
import android.annotation.TargetApi;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import android.util.DisplayMetrics;
import android.util.Log;
import java.nio.MappedByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ExtraUtil
{
    //Android Version
    private static int SDK = Build.VERSION.SDK_INT;
    
    /**
     * Convert the current time in milliseconds to readable string as given format.
     * Notice: The time is counted since January 1, 1970 00:00:00.0 UTC.
     * @param format
     *     y year
     *     M month in the year
     *     d day
     *     h hours(12)
     *     H hours(24)
     *     m minute
     *     s second
     *     S millisecond
     *     E weekday
     *     D days in the year
     */
    public static String convertMillisTime(long time_millis, String format)
    {
        if (time_millis <= 0)
        {
            return "";
        }
        String result = "";
        try
        {
            SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.US);
            result = dateFormat.format(new Date(time_millis));
        }
        catch (IllegalArgumentException e)
        {
            e.printStackTrace();
        }
        return result;
    }
    
    /**
     * Convert the current time in milliseconds to readable string
     * as default format ("yyyy-MM-dd HH:mm:ss").
     */
    public static String convertMillisTime(long time_millis)
    {
        return convertMillisTime(time_millis, "yyyy-MM-dd HH:mm:ss");
    }
    
    /**
     * @param name App name (Part of it is OK), package name or apk path.
     */
    public static PackageInfo getPackageInfo(Context context, String name)
    {
        name = name.trim();
        if (TextUtils.isEmpty(name))
        {
            return null;
        }
        
        PackageInfo result = null;
        
        //Search by apk path.
        if (name.contains("/") && (new File(name)).isFile())
        {
            if (SDK >= 14)
            {
                result = context.getPackageManager().getPackageArchiveInfo(name,
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
            }
            else
            {
                result = getPackageArchiveInfo(name,
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
            }
            if (result != null)
            {
                result.applicationInfo.sourceDir = name;
                result.applicationInfo.publicSourceDir = name;
            }
        }
        
        //Search by package name.
        if (result == null && name.contains("."))
        {
            try
            {
                result = context.getPackageManager().getPackageInfo(name,
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
            }
            catch (PackageManager.NameNotFoundException e)
            {
                e.printStackTrace();
            }
        }

        //Make it easy to match.
        name = name.toLowerCase();
        
        //Search by app name.
        if (result == null)
        {
            List<PackageInfo> packages = context.getPackageManager()
                .getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
            if (packages == null)
            {
                return null;
            }
            
            //For matching next.
            List<String> app_names = new ArrayList<>();
            String temp_app_name;
            
            //Firstly, match by complete app name.
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo;
            for (PackageInfo packageInfo : packages)
            {
                applicationInfo = packageInfo.applicationInfo;
                temp_app_name = applicationInfo.loadLabel(packageManager)
                    .toString().toLowerCase().trim();
                app_names.add(temp_app_name);
                if (temp_app_name.equals(name))
                {
                    result = packageInfo;
                    break;
                }
            }
            
            //If there is no result, continue to match by prefix of app name.
            if (result == null)
            {
                for (int i = 0, len = app_names.size(); i < len; ++ i)
                {
                    if (app_names.get(i).startsWith(name))
                    {
                        result = packages.get(i);
                        break;
                    }
                }
            }
            
            //If there is no result, continue to match by part of app name.
            if (result == null)
            {
                for (int i = 0, len = app_names.size(); i < len; ++ i)
                {
                    if (app_names.get(i).contains(name))
                    {
                        result = packages.get(i);
                        break;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Get the apk path of this app, always located in /data/app/.
     */
    public static File pickUpMyPackage(Context context)
    {
        PackageInfo packageInfo = getPackageInfo(context, context.getPackageName());
        if (packageInfo == null)
        {
            return null;
        }
        
        File file_s = new File(packageInfo.applicationInfo.publicSourceDir);
        File file_t = new File(context.getExternalCacheDir(), getVerInfo(context, true));
        //If the same file exists, just return it.
        if (file_t.exists() && file_s.length() == file_t.length())
        {
            return file_t;
        }
        
        fileChannelCopy(file_s, file_t);

        return file_t.exists() ? file_t : null;
    }
    
    /**
     * Get package names of all installed apps.
     * @param with_system Mark if system apps are included or not.
     */
    public static String[] getAllInstalledPackageNames(Context context, boolean with_system)
    {
        List<ApplicationInfo> packages = context.getPackageManager()
            .getInstalledApplications(0);
        if (packages == null)
        {
            return null;
        }

        List<String> result = new ArrayList<>();

        for (ApplicationInfo applicationInfo : packages)
        {
            //Skip over system apps.
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1 && !with_system)
            {
                continue;
            }

            result.add(applicationInfo.packageName);
        }
        
        Collections.sort(result);

        return result.toArray(new String[result.size()]);
    }

    /**
     * Effective way to copy files by file channel.
     * @return Return the number of copied files.
     */
    public static int fileChannelCopy(File[] sources, File[] targets)
    {
        int result = 0;
        if (sources == null || targets == null)
        {
            return result;
        }
        
        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel fc_in = null;
        FileChannel fc_out = null;
        try
        {
            for (int i = 0, len_s = sources.length, len_t = targets.length;
                i < len_s && i < len_t; ++ i)
            {
                if (sources[i] == null || targets[i] == null)
                {
                    continue;
                }
                
                fis = new FileInputStream(sources[i]);
                fos = new FileOutputStream(targets[i]);
                fc_in = fis.getChannel();
                fc_out = fos.getChannel();
                fc_in.transferTo(0, fc_in.size(), fc_out);
                
                ++ result;
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (fc_out != null)
            {
                try
                {
                    fc_out.close();
                }
                catch (IOException e)
                {}
            }
            if (fos != null)
            {
                try
                {
                    fos.close();
                }
                catch (IOException e)
                {}
            }
            if (fc_in != null)
            {
                try
                {
                    fc_in.close();
                }
                catch (IOException e)
                {}
            }
            if (fis != null)
            {
                try
                {
                    fis.close();
                }
                catch (IOException e)
                {}
            }
        }
        
        return result;
    }

    /*public static int fileChannelCopy(String source_path, String target_path)
    {
        if (source_path == null || target_path == null)
        {
            return 0;
        }
        
        File[] sources = { new File(source_path) };
        File[] targets = { new File(target_path) };
        
        return fileChannelCopy(sources, targets);
    }*/
    
    public static int fileChannelCopy(File source, File target)
    {
        return fileChannelCopy(new File[] { source }, new File[] { target });
    }
    
    /**
     * Read text file from its InputStream.
     * @param charset Charset of target file.
     * @param lines_limit The max lines to read.
     */
    public static String readFile(InputStream inputStream, String charset, int lines_limit)
    {
        StringBuilder stringBuilder = new StringBuilder();
        
        try
        {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, charset);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            int i = 0;
            String str_buffer;
            while (++ i <= lines_limit && (str_buffer = bufferedReader.readLine()) != null)
            {
                stringBuilder.append(str_buffer).append("\n");
            }
            
            if (i >= lines_limit && lines_limit != 1)
            {
                //Indicate that the content is part of the file.
                stringBuilder.append(String.format("...  >%d!", lines_limit));
            }
            else
            {
                //Remove the final superfluous "\n".
                stringBuilder.setLength(stringBuilder.length() - 1);
            }
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (inputStream != null)
            {
                try
                {
                    inputStream.close();
                }
                catch (IOException e)
                {}
            }
        }

        return stringBuilder.toString();
    }
    
    public static String readFile(InputStream inputStream, int lines_limit)
    {
        return readFile(inputStream, "UTF-8", lines_limit);
    }
    
    public static String readFile(InputStream inputStream, String charset)
    {
        return readFile(inputStream, charset, Integer.MAX_VALUE);
    }
    
    /**
     * Read text file from its InputStream.
     */
    public static String readFile(InputStream inputStream)
    {
        return readFile(inputStream, Integer.MAX_VALUE);
    }
    
    /**
     * Read certain text file.
     */
    public static String readFile(File file)
    {
        String result = "";
        
        try
        {
            result = readFile(new FileInputStream(file));
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Read certain text file.
     * @param file_str The path of reading text file.
     */
    public static String readFile(String file_str)
    {
        return readFile(new File(file_str));
    }
    
    public static String fileChannelRead(String file_str)
    {
        String result = "";
        
        FileInputStream fileInputStream = null;
        try
        {
            fileInputStream = new FileInputStream(file_str);
            FileChannel fileChannel = fileInputStream.getChannel();
            MappedByteBuffer mappedByteBuffer = fileChannel
                .map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            result = Charset.defaultCharset().decode(mappedByteBuffer).toString();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (fileInputStream != null)
            {
                try
                {
                    fileInputStream.close();
                }
                catch (IOException e)
                {}
            }
        }
        
        return result;
    }
    
    /**
     * Save text to certain file.
     * @param target_file The file to load the text.
     */
    public static boolean saveFile(File target_file, String text)
    {
        boolean result = false;
        FileOutputStream fileOutputStream = null;
        try
        {
            fileOutputStream = new FileOutputStream(target_file);
            //Charset: UTF-8 (default)
            fileOutputStream.write(text.getBytes());
            result = true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (fileOutputStream != null)
            {
                try
                {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                }
                catch (IOException e)
                {}
            }
        }
        return result;
    }
    
    /**
     * Create string of version info about this app.
     * @param is_file_name If set "true", the string like "com.by_syk.osbuild_v1.0(150309).apk",
     *     else "OSBuild_v1.0(150309)".
     */
    public static String getVerInfo(Context context, boolean is_file_name)
    {
        final String APP_NAME = context.getString(R.string.app_name);
        String package_name = "";
        String ver_name = "";
        int ver_code = 0;
        try
        {
            PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            package_name = packageInfo.packageName;
            ver_name = packageInfo.versionName;
            ver_code = packageInfo.versionCode;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }
        
        if (is_file_name)
        {
            return String.format("%1$s_v%2$s(%3$d).apk", package_name, ver_name, ver_code);
        }
        else
        {
            return String.format("%1$s_v%2$s(%3$d)", APP_NAME, ver_name, ver_code);
        }
    }
    
    /**
     * Create string of version info about certain app, like "com.by_syk.OSBuild_v1.0(150309).apk".
     */
    public static String getVerInfo(final PackageInfo PACKAGEINFO)
    {
        final String PACKAGE_NAME = PACKAGEINFO.packageName;
        final String VER_NAME = PACKAGEINFO.versionName;
        final int VER_CODE = PACKAGEINFO.versionCode;
        
        return String.format("%1$s_v%2$s(%3$d).apk", PACKAGE_NAME, VER_NAME, VER_CODE);
    }
    
    /**
     * Get max, min CPU frequency.
     * Read "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
     * "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq"
     */
    /*public static String getCPUFreq(boolean max)
    {
        final String[] ARGS_MAX = { "/system/bin/cat",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq" };
        final String[] ARGS_MIN = { "/system/bin/cat",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq" };

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(max ? ARGS_MAX[1] : ARGS_MIN[1]);
        stringBuilder.append(L1).append(max ? "cpuinfo_max_freq: " : "cpuinfo_min_freq: ");

        InputStream inputStream = null;
        try
        {
            ProcessBuilder processBuilder = new ProcessBuilder(max ? ARGS_MAX : ARGS_MIN);
            Process process = processBuilder.start();
            inputStream = process.getInputStream();
            String temp = "";
            byte[] buffer = new byte[24];
            while (inputStream.read(buffer) != -1)
            {
                temp += (new String(buffer));
            }
            temp = temp.trim();
            if (!"".equals(temp))
            {
                int freq = Integer.parseInt(temp);
                stringBuilder.append(freq);
                stringBuilder.append(SPACE).append(UnitUtil.convertFreq(freq));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (inputStream != null)
            {
                try
                {
                    inputStream.close();
                }
                catch (IOException e)
                {}
            }
        }

        return stringBuilder.toString();
    }*/
    
    /**
     * Get RAM size by this method if API level < 16.
     * Read "/proc/meminfo".
     */
    public static long getTotalRAM()
    {
        long result = -1;//Unit: byte
        
        final String TEXT = readFile("/proc/meminfo");
        if ("".equals(TEXT))
        {
            return result;
        }
        
        Pattern pattern = Pattern.compile("[^0-9]");   
        Matcher matcher = pattern.matcher(TEXT.substring(0, TEXT.indexOf("\n")));   
        String temp = matcher.replaceAll("").trim();
        if (!"".equals(temp))
        {
            result = UnitUtil.toIntSafely(temp);//Unit: KB
        }

        return result;
    }
    
    /**
     * Simplify the width-height ratio.
     * The value is integer to integer.
     */
    public static String getWHRatioInt(int width, int height)
    {
        int p1 = width;
        int p2 = height;
        int temp;
        while (p2 != 0)
        {
            temp = p1 % p2;
            p1 = p2;
            p2 = temp;
        }
        
        return String.format("%1$d:%2$d", width / p1, height / p1);
    }
    
    /**
     * Get CPU cores.
     * Read “/sys/devices/system/cpu/cpu[0-9]/"
     */
    public static int getCPUCores()
    {
        //Strategy 1.
        //The value may less than real value.
        //int cores_runtime = Runtime.getRuntime().availableProcessors();
        
        //Strategy 2.
        int cores = 1;
        class CpuFilter implements FileFilter
        {
            @Override
            public boolean accept(File pathname)
            {
                return Pattern.matches("cpu[0-9]", pathname.getName());
            }      
        }
        try
        {
            File dir = new File("/sys/devices/system/cpu/");
            File[] files = dir.listFiles(new CpuFilter());
            cores = files.length;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return cores;
    }
    
    /**
     * Get storage info (RootDirectory, DataDirectory, ExternalStorageDirectory, etc.).
     * @return array like [total, available]. Unit: byte
     */
    @TargetApi(18)
    @SuppressWarnings("deprecation")
    public static long[] getStorageUsage(File storage)
    {
        long[] result = { -1, -1 };
        
        if (storage == null || !storage.exists())
        {
            return result;
        }
        
        StatFs statFs = new StatFs(storage.getPath());
        long block_size;//Unit: byte
        long block_count;
        long avail_blocks;
        if (SDK >= 18)
        {
            block_size = statFs.getBlockSizeLong();
            block_count = statFs.getBlockCountLong();
            avail_blocks = statFs.getAvailableBlocksLong();
        }
        else
        {
            block_size = statFs.getBlockSize();
            block_count = statFs.getBlockCount();
            avail_blocks = statFs.getAvailableBlocks();
        }
        
        result[0] = block_size * block_count;//Unit: byte
        result[1] = block_size * avail_blocks;//Unit: byte
        
        return result;
    }
    
    /**
     * Read file which requires root permission.
     * Method 1: Copy it to external storage and then read it.
     */
    /*public static String readFileRoot(Context context, File source_file)
    {
        if (source_file == null)
        {
            return "";
        }
        File target_file = new File(context.getExternalCacheDir(),
            source_file.getName());
        String script = String.format("cp %1$s %2$s",
            source_file, target_file);

        Process process = null;
        PrintWriter printWriter;
        try
        {
            process = Runtime.getRuntime().exec("su");
            printWriter = new PrintWriter(process.getOutputStream());
            printWriter.println(script);
            printWriter.flush();
            printWriter.println("exit");
            printWriter.flush();
            
            //Causes the calling thread to wait for the native process
            //associated with this object to finish executing.
            process.waitFor();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
            }
        }

        if (target_file.exists())
        {
            return readFile(target_file);
        }
        return "";
    }*/
    
    /**
     * Read file which requires root permission.
     * Method 2: Read it by command "cat" directly.
     */
    public static String readFileRoot(File source_file)
    {
        if (source_file == null)
        {
            return "";
        }
        String result = "";
        final String SCRIPT = "cat " + source_file;
        
        Process process = null;
        PrintWriter printWriter;
        try
        {
            //Execute command.
            process = Runtime.getRuntime().exec("su");
            printWriter = new PrintWriter(process.getOutputStream());
            printWriter.println(SCRIPT);
            printWriter.flush();
            printWriter.println("exit");
            printWriter.flush();
            
            //Read file.
            result = readFile(process.getInputStream());
            
            //Causes the calling thread to wait for the native process
            //associated with this object to finish executing.
            process.waitFor();
        }
        /*catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }*/
        catch (Exception e)
        {
            //Many ROMs cause many flaws to arrive here. 
            e.printStackTrace();
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
            }
        }
        
        return result;
    }
    
    public static String getBusyBoxVer()
    {
        String text = "";
        Process process = null;
        try
        {
            //Execute command.
            process = Runtime.getRuntime().exec("busybox");
            
            //Read file (one line is enough).
            text = readFile(process.getInputStream(), 1);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
            }
        }
        
        if (!TextUtils.isEmpty(text))
        {
            //Like this:
            //BusyBox v1.22.1 bionic (2015-05-25 18:22 +0800) multi-call binary.
            String[] infos = text.split(" ");
            if (infos[0].equalsIgnoreCase("busybox"))
            {
                if (infos[1].startsWith("v") || infos[1].startsWith("V"))
                {
                    return infos[1].substring(1);
                }
            }
        }
        
        return "";
    }
    
    /**
     * Format file name.
     * If it's a directory, modify it like "/x/x/";
     * else modify it like "/x/x".
     */
    public static String getPathRuled(File file)
    {
        if (file == null)
        {
            return "";
        }
        
        if (file.isDirectory())
        {
            return file.getPath() + "/";
        }
        
        return file.getPath();
    }
    
    /**
     * Format file name.
     * If it's a directory, modify it like "/x/x/";
     * else modify it like "/x/x".
     * @param file_str The file path.
     */
    public static String getPathRuled(String file_str)
    {
        if (file_str == null)
        {
            return "";
        }
        
        return getPathRuled(new File(file_str));
    }
    
    public static String makeStrs(char ch, int len)
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < len; ++ i)
        {
            stringBuilder.append(ch);
        }
        
        return stringBuilder.toString();
    }
    
    /**
     * Get characters' unicode in hexadecimal.
     */
    public static String getUnicode(String text, int len_limit)
    {
        if (TextUtils.isEmpty(text) || len_limit <= 0)
        {
            return "";
        }
        
        StringBuilder stringBuilder = new StringBuilder();
        
        final char[] CHARS = text.toCharArray();
        for (int len = CHARS.length, i = len - 1; i >= 0; -- i)
        {
            if (len - i <= len_limit)
            {
                stringBuilder.insert(0, Integer.toHexString(CHARS[i]).toUpperCase());
                stringBuilder.insert(0, " ");
            }
            else
            {
                stringBuilder.insert(0, " ...");
                break;
            }
        }
        
        //Remove the first superfluous " ".
        return stringBuilder.substring(1);
    }
    
    /*public static String getUnicode(char ch)
    {
        final String HEX = Integer.toHexString(ch).toUpperCase();
        switch (HEX.length())
        {
            case 3:
                return "0" + HEX;
            case 2:
                return "00" + HEX;
            case 1:
                return "000" + HEX;
            default://case 4:
                return HEX;
        }
    }*/
    
    /**
     * Get online CPU cores.
     * @param online_info Text content of “/sys/devices/system/cpu/online".
     */
    public static int getCPUOnlineCores(String online_info)
    {
        if (TextUtils.isEmpty(online_info))
        {
            return -1;
        }

        int result = 0;
        
        String[] nums;
        for (String temp_str : online_info.split(","))
        {
            nums = temp_str.split("-");
            switch (nums.length)
            {
                case 1:
                    ++ result;
                    break;
                case 2:
                    result += (Integer.parseInt(nums[1])
                        - Integer.parseInt(nums[0])) + 1;
            }
        }
        
        return result;
    }
    
    /**
     * Because of a BUG of Android (API 13-),
     * get signature info by using "getPackageArchiveInfo" of "PackageManager"
     * always causes "NullPointerException".
     * Lack of code in method "getPackageArchiveInfo":
     *     if ((flags & GET_SIGNATURES) != 0)
     *     {
     *         packageParser.collectCertificates(pkg, 0);
     *     }
     */
    @SuppressWarnings("unchecked")
    public static PackageInfo getPackageArchiveInfo(String archiveFilePath, int flags)
    {
        try
        {
            Class packageParserClass = Class.forName("android.content.pm.PackageParser");
            Class[] innerClasses = packageParserClass.getDeclaredClasses();
            Class packageParserPackageClass = null;
            for (Class innerClass : innerClasses)
            {
                if (0 == innerClass.getName().compareTo("android.content.pm.PackageParser$Package"))
                {
                    packageParserPackageClass = innerClass;
                    break;
                }
            }
            Constructor packageParserConstructor = packageParserClass.getConstructor(String.class);
            Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage",
                File.class, String.class, DisplayMetrics.class, int.class);
            Method collectCertificatesMethod = packageParserClass.getDeclaredMethod("collectCertificates",
                packageParserPackageClass, int.class);
            Method generatePackageInfoMethod = packageParserClass.getDeclaredMethod("generatePackageInfo",
                packageParserPackageClass, int[].class, int.class, long.class, long.class);
            packageParserConstructor.setAccessible(true);
            parsePackageMethod.setAccessible(true);
            collectCertificatesMethod.setAccessible(true);
            generatePackageInfoMethod.setAccessible(true);

            Object packageParser = packageParserConstructor.newInstance(archiveFilePath);

            DisplayMetrics displayMetrics = new DisplayMetrics();
            displayMetrics.setToDefaults();

            final File sourceFile = new File(archiveFilePath);

            Object pkg = parsePackageMethod.invoke(packageParser, sourceFile,
                archiveFilePath, displayMetrics, 0);
            if (pkg == null)
            {
                return null;
            }

            if ((flags & PackageManager.GET_SIGNATURES) != 0)
            {
                collectCertificatesMethod.invoke(packageParser, pkg, 0);
            }

            return (PackageInfo)generatePackageInfoMethod.invoke(null, pkg, null, flags, 0, 0);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }
}
