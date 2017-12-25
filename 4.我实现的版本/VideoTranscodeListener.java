package com.fcar.fcarloan.metaq.consumer;

import com.alibaba.fastjson.JSON;
import com.fcar.base.common.enums.TranscodeStatusEnum;
import com.fcar.base.loan.dto.ImageDataDTO;
import com.fcar.base.loan.dto.ImageDataDTO;
import com.fcar.base.zimg.CarResourcesClient;
import com.fcar.fcarloan.common.exception.BusinessException;
import com.fcar.fcarloan.remote.client.TransCodeRemoteClient;
import com.zuche.confcenter.util.DefaultHttpClient;
import com.zuche.framework.common.RequestContext;
import com.zuche.framework.metaq.handler.DefaultExecutorMessageListener;
import com.zuche.framework.metaq.vo.MessageVO;
import com.zuche.framework.remote.nio.codec.HessianSerializerUtils;
import com.zuche.logger.client.util.PropertiesReader;
import it.sauronsoftware.jave.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by zhangzhenhua on 2017/12/12.
 */
public class VideoTranscodeListener extends DefaultExecutorMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(RepayStatusMsgListener.class);

    private static final String TEMP_FILE_PATH = PropertiesReader
            .getAppointPropertiesAttribute("zimg", "LOCAL_FILE_PATH",
                    String.class);

    private static final String preUrl = PropertiesReader
            .getAppointPropertiesAttribute("ftpUploadPathConfig", "carresources.url",
                    String.class);

    @Override
    public void handlerMessage(MessageVO message) {
        synchronized(this){
            ImageDataDTO dto = null;
            boolean upload = false;
            boolean delete = false;
            String hbaseNewPath = "";
            int transStatus = TranscodeStatusEnum.SEND.getIndex();
            try{
                byte[] data = message.getData();
                if(data != null && data.length > 0){
                    dto = (ImageDataDTO) HessianSerializerUtils.deserialize(data);
                    logger.error("视频转码，入参：" + JSON.toJSONString(dto));
                    //拼接不同环境的url名
                    String pathDto = dto.getImageUrl();
                    String path = getImageFullUrl(pathDto);

                    try {
                        String fileName = getFileName(path);
                        String tempPath = getVideoResource(path,fileName);
                        String newPath = "";

                        File temp = new File(tempPath);
                        if(!temp.exists() || temp.length() == 0){
                            logger.error("从hbase上拿下的视频有问题,直接返回");
                            dto.setTranscodeStatus(TranscodeStatusEnum.FAIL.getIndex());
                        }else{
                            //1.验证是否需要转码
                            boolean isNeedTrans = ValidTransCode(tempPath);
                            System.out.println("是否需要转码 " + isNeedTrans);
                            if(isNeedTrans){
                                //2.转码
                                newPath = process(tempPath);

                                if(!"".equals(newPath)){
                                    //3.上传
                                    hbaseNewPath = uploadVideo(newPath);
                                }else{
                                    transStatus = TranscodeStatusEnum.FAIL.getIndex();
                                }
                                if(!"".equals(hbaseNewPath)){
                                    transStatus = TranscodeStatusEnum.SUCCESS.getIndex();
                                }
                            }else{
                                //不需要转码的视频处理
                                transStatus = TranscodeStatusEnum.SUCCESS.getIndex();
                                hbaseNewPath = dto.getImageUrl();
                            }
                            //4.删除本地文件
                            delete = deleteFile(tempPath,newPath);
                            System.out.println("回调的path" + hbaseNewPath);
                        }
                        //5.组合上述信息回调*//*
                        boolean callbackStatus = videoTransCallBack(dto,hbaseNewPath,transStatus);

                        if(callbackStatus){
                            logger.error("视频转码全部完成！");
                        }
                    } catch (Exception e) {
                        logger.error("视频转码mq消费异常",e);
                    }
                }
            }catch(Exception e){
                logger.error("视频转码mq消费异常",e);
            }
        }
    }

    private String getFileName(String path){
        String fileName = "";
        fileName = path.substring(path.lastIndexOf("/")+1);
        return fileName;
    }

    //验证是否有转码的必要
    private boolean ValidTransCode(String filepath){
        boolean flag = false;
        Encoder encoder = new Encoder();
        File video = new File(filepath);

        try {
            MultimediaInfo videoInfo = encoder.getInfo(video);
            if(!("h264".equals(videoInfo.getVideo().getDecoder()) || "flv".equals(videoInfo.getVideo().getDecoder()) || "webm".equals(videoInfo.getVideo().getDecoder()))){
                //验证编码格式，jave的列表中
                flag = true;
            }

        } catch (EncoderException e) {
            logger.error("验证是否需要转码异常" + e);
            e.printStackTrace();
        }
        return flag;
    }

    //从hbase拿到本地
    private String getVideoResource(String path, String filename){
        URL url;
        String resultPath = "";
        try {
            url = new URL(path);
            //URLConnection conn = url.openConnection();

            HttpURLConnection connection = null;
            connection = DefaultHttpClient.getConnection(url, "POST","application/x-www-form-urlencoded;charset=utf-8",20*60*1000,10000);
            InputStream fis = connection.getInputStream();

            //将视频放到服务器对应路径下
            String relativePath = TEMP_FILE_PATH;
            FileOutputStream fos=new FileOutputStream(relativePath + File.separator + filename);
            int read;
            byte b[]=new byte[1024];
            //读取文件，存入字节数组b，返回读取到的字符数，存入read,默认每次将b数组装满
            read=fis.read(b);
            while(read!=-1)
            {
                fos.write(b,0,read);
                read=fis.read(b);
            }

            resultPath = relativePath + File.separator + filename;
            fis.close();
            fos.close();
        } catch (MalformedURLException e) {
            logger.error("hbase视频拿到本地异常",e);
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            logger.error("hbase视频拿到本地异常",e);
            e.printStackTrace();
        } catch (IOException e) {
            logger.error("hbase视频拿到本地异常",e);
            e.printStackTrace();
        }
        return resultPath;
    }

    private String process(String oldpath){

        String newPath = "";
        try {
            newPath = TEMP_FILE_PATH + File.separator + System.currentTimeMillis() + ".mp4";

            File source = new File(oldpath);
            File target = new File(newPath);

            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("libmp3lame");

            VideoAttributes video = new VideoAttributes();
            video.setCodec("flv");
            video.setBitRate(new Integer(360000));
            video.setFrameRate(new Integer(30));
            video.setSize(new VideoSize(400, 300));

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setFormat("flv");
            attrs.setAudioAttributes(audio);
            attrs.setVideoAttributes(video);
            Encoder encoder = new Encoder();

            //获取编码信息

            MultimediaInfo beforeStatus = encoder.getInfo(source);


            encoder.encode(source, target, attrs);




            //通过转后的状态判断
            if(!target.exists() || target.length() == 0){
                return "";
            }
        } catch (IllegalArgumentException e) {
            logger.error("转码encode过程中出现异常",e);
            return "";
        } catch (InputFormatException e) {
            logger.error("转码encode过程中出现异常",e);
            return "";
        } catch (EncoderException e) {
            logger.error("转码encode过程中出现异常",e);
            return "";
        }
        return newPath;
    }

    //删除暂存文件
    public boolean deleteFile(String sourcepath,String targetpath) throws Exception {
        boolean flag = false;
        try {
            File source = new File(sourcepath);
            File target = new File(targetpath);
            // 路径为文件且不为空则进行删除
            if (source.isFile() && source.exists()) {
                source.delete();
                flag = true;
            }

            if (target.isFile() && target.exists()) {
                target.delete();
                flag = true;
            }
            if(flag){
                logger.error("临时视频文件删除成功！");
            }
        } catch (Exception e) {
            logger.error("删除临时文件出现异常",e);
            e.printStackTrace();
        }
        return flag;
    }

    //视频上传hbase
    public String uploadVideo(String localpath){
        String hbasepath = "";
        File file = new File(localpath);
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            hbasepath = CarResourcesClient.getInstance().upload(inputStream, "mp4");

        } catch (FileNotFoundException e) {
            logger.error("转码上传视频找不到文件",e);
            return "";
        }catch(Exception e){
            logger.error("上传视频到hbase失败",e);
            return "";
        }
        return hbasepath;
    }

    //转码之后的回调方法
    public boolean videoTransCallBack(ImageDataDTO dto, String path, int transStatus){
        boolean flag = false;
        dto.setTranscodeImageUrl(path);
        dto.setTranscodeStatus(transStatus);

        try {
            flag = TransCodeRemoteClient.transCodeCallBack(dto);
        } catch (BusinessException e) {
            logger.error("回调transCodeCallBack方法失败",e);
        }
        return flag;
    }

    //获取完整的视频路径
    private String getImageFullUrl(String url) {
        if (url.indexOf("http:") == -1 && url.indexOf("https:") == -1) {
            String index = preUrl + "/resource/";
            if (null != RequestContext.getSession()) {
                index = RequestContext.getSession().getAttribute("carresources_MA") + "/resource/";
            }
            return index + url;
        } else {
            return url;
        }
    }
}