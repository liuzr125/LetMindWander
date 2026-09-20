package com.zhixing.service;

import com.zhixing.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

/** Only a pinned, reviewed catalogue is fetchable; no user URLs or redirects. */
@Component
public class OnlineVocabularyDownloader {
    public static final String REVISION = "3992bcb94c800a2fd38a9fd6ff95b2353e755363";
    private static final Set<String> FILES = new HashSet<String>(Arrays.asList(
        "1521164654696_KaoYan_2.zip", "1521164658897_KaoYan_3.zip",
        "1521164669833_KaoYan_1.zip", "1521164661106_KaoYanluan_1.zip",
        "1521164647926_ChuZhong_2.zip", "1521164675301_GaoZhong_2.zip",
        "1521164635506_CET4_2.zip", "1524052554766_CET6_2.zip",
        "1521164657744_IELTS_2.zip", "1521164640451_TOEFL_2.zip",
        "1521164637271_GRE_2.zip"));
    public byte[] download(String revision, String file) {
        if (!REVISION.equals(revision) || !FILES.contains(file))
            throw error("ONLINE_SOURCE_NOT_ALLOWED", "只能下载已核对且锁定版本的目录资源");
        HttpURLConnection connection = null;
        try {
            connection=(HttpURLConnection)new URL("https://raw.githubusercontent.com/kajweb/dict/"+revision+"/book/"+file).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000); connection.setReadTimeout(30000);
            if(connection.getResponseCode()!=200) throw new IOException("Download failed");
            try(InputStream in=connection.getInputStream()) { return bounded(in,8*1024*1024); }
        } catch(IOException e) { throw error("ONLINE_DOWNLOAD_FAILED","下载失败或超过大小限制；原有快照未修改，请稍后重试"); }
        finally { if(connection!=null) connection.disconnect(); }
    }
    public static byte[] bounded(InputStream in,int maximum) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n,total=0;
        while((n=in.read(buf))!=-1) { total+=n;if(total>maximum)throw new IOException("Size limit");out.write(buf,0,n); }
        return out.toByteArray();
    }
    public String unpack(byte[] archive,String code) {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry; byte[] content=null; int total=0,entries=0;
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>10)throw new IOException("Entry limit");
                if(entry.isDirectory())continue;
                byte[] bytes=bounded(zip,32*1024*1024-total);total+=bytes.length;
                // Never extract paths or execute contents.
                if(entry.getName().equals(code+".json")) {
                    if(content!=null)throw new IOException("Duplicate entry");
                    content=bytes;
                } else if(!entry.getName().startsWith("__MACOSX/")) throw new IOException("Unexpected entry");
            }
            if(content==null)throw new IOException("Missing JSON");
            return new String(content,java.nio.charset.StandardCharsets.UTF_8);
        } catch(IOException e) { throw error("ONLINE_ARCHIVE_INVALID","词表压缩包格式或大小不符合预期"); }
    }
    private ApiException error(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
}
