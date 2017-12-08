package video;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ConvertH264 {
	
	private final static String PATH = "D:\\special.mp4";
	private final static String OUTPATH = "D:\\FfmpegFile\\output";
	
	public static void main(String[] args) {

		if(!checkfile(PATH)){
			System.out.println("源文件不是一个完整的文件，请检查");
			return;
			//文件格式有误处理 -- 和史宏商量(最终给出visio版本的流程图)
			//1.不进行转码
			//2.考虑重新上传视频
		}
	
		process();
	}
	
	/**
	 * 执行转码，期间对源视频是否被ffmpeg支持进行验证
	 * @return 状态
	 */
	private static int process(){
		int status = checkContentType();
		if(status == 0){
			processH264(PATH);
		}else if(status == 1){
			String tempPath = processAVI(PATH);
			if(!"ERROR".equals(tempPath)){
				processH264(tempPath);
			}
		}
		return status;
	}
	

	/**
	 * 检查是否为一个完整的文件
	 * @param path
	 * @return
	 */
	private static boolean checkfile(String path) {  
        File file = new File(path);  
        if (!file.isFile()) {  
            return false;  
        }  
        return true;  
    }  
	
	/**
	 * check contentType 
	 * @return int 0-能解析； 1-不能解析
	 */
	private static int checkContentType() {  
        String type = PATH.substring(PATH.lastIndexOf(".") + 1, PATH.length())  
                .toLowerCase();  
        //ffmpeg能解析的格式：（asx，asf，mpg，wmv，3gp，mp4，mov，avi，flv等）  
        if (type.equals("avi")) {  
            return 0;  
        } else if (type.equals("mpg")) {  
            return 0;  
        } else if (type.equals("wmv")) {  
            return 0;  
        } else if (type.equals("3gp")) {  
            return 0;  
        } else if (type.equals("mov")) {  
            return 0;  
        } else if (type.equals("mp4")) {  
            return 0;  
        } else if (type.equals("asf")) {  
            return 0;  
        } else if (type.equals("asx")) {  
            return 0;  
        } else if (type.equals("flv")) {  
            return 0;  
        }  
        //对ffmpeg无法解析的文件格式(wmv9，rm，rmvb等),策略是先转换成avi格式  
        else if (type.equals("wmv9")) {  
            return 1;  
        } else if (type.equals("rm")) {  
            return 1;  
        } else if (type.equals("rmvb")) {  
            return 1;  
        }  
        return 2;  
    }
	
	//暂时按照时间生成文件名（之后和史宏再商榷）
	private static String generateFileName(){
		Calendar c = Calendar.getInstance();  
	    return String.valueOf(c.getTimeInMillis())+ Math.round(Math.random() * 100000); 
	    
	}
	
	
	//注意ffmpeg一定要提前编译h264编码格式
	private static boolean processH264(String oldpath){
		//码率 --  尺寸 -- 432*240  源帧率 -- 29  位率（继续调试，获得相对最清楚的版本）
        
        String savename = generateFileName();
        List<String> commend = new ArrayList<String>(); 
        
    	//ffmpeg.exe的路径地址，下个版本，和程序地址同步，resource文件中
        commend.add("D:\\ffmpeg\\ffmpeg");  
        commend.add("-i");  
        commend.add(oldpath); 
        commend.add("-ab");  
        commend.add("56");  
        commend.add("-ar");  
        commend.add("22050");
        commend.add("-vcodec");
        commend.add("h264");
        commend.add("-qscale");  
        commend.add("8");  
        commend.add("-r");  
        commend.add("15");  
        commend.add("-s");  
        commend.add("600*500");
        commend.add("D:\\" + savename + ".mp4");  
  
        try {   
            //调用线程命令进行转码
            ProcessBuilder builder = new ProcessBuilder(commend);                 
            builder.command(commend);  
            builder.start();  
  
            return true;  
        } catch (Exception e) {  
            e.printStackTrace();  
            return false;  
        }  
	}	
	
	private static String processAVI(String oldpath){
		String saveName = generateFileName();

		List<String> commend = new ArrayList<String>();
		commend.add("D\\ffmpeg\\mencoder");
		commend.add("-oac");  
		commend.add("lavc");  
		commend.add("-lavcopts");  
		commend.add("acodec=mp3:abitrate=64");  
		commend.add("-ovc");  
		commend.add("xvid");  
		commend.add("-xvidencopts");  
		commend.add("bitrate=600");  
		commend.add("-of");  
		commend.add("avi");  
		commend.add("-o");  
		commend.add("D:\\FfmpegFile\\output" + saveName + ".avi");  
		
		try{
			return OUTPATH + saveName + ".avi";
		}catch(Exception e){
			e.printStackTrace();
			return "ERROR";
		}
	}
}