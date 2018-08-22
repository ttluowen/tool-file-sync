package com.yy.tool.filesync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import com.rt.log.Logger;
import com.rt.util.array.ArrayUtil;
import com.rt.util.file.FileUtil;
import com.rt.util.number.NumberUtil;
import com.rt.util.proterty.PropertyUtil;
import com.rt.util.string.StringUtil;
import com.rt.web.config.SystemConfig;

/**
 * 文件变化监视器。
 * 
 * @since 2018-02-21
 * @version 1.0
 * @author Luowen
 */
public class FileSync {
	
	/** 默认差异监听时期，单位秒。 */
	private static final int DEFAULT_WATCH_INTERVAL = 2;
	

	/** 监听目录。 */
	private String[] watchPath;
	/** 同步目录。 */
	private String[] syncPath;
	
	/** 差异监听周期，单位秒。 */
	private int watchInterval = DEFAULT_WATCH_INTERVAL;

	/** 排除的文件类型。 */
	private List<String> excludeExts = new ArrayList<>();

	/** 排除的文件或文件夹列表。 */
	private List<String> excludeFiles = new ArrayList<>();
	

	/** 文件变化监视器。 */
	private FileAlterationMonitor[] monitor;

	
	/**
	 * 启动监视器。
	 * 
	 * @throws Exception
	 */
	public void stop() throws Exception {

		for (FileAlterationMonitor item : monitor) {
			item.stop();
		}
	}

	
	/**
	 * 关闭监视器。
	 * 
	 * @throws Exception
	 */
	public void start() throws Exception {

		for (FileAlterationMonitor item : monitor) {
			item.start();
		}
	}

	
	/**
	 * 系统信息初始化。
	 * 
	 * @param second
	 * @throws IOException 
	 */
	private void init() throws IOException {
	
		// 获取当前运行位置。
		String path = System.getProperty("user.dir") + "\\";
	
		
		// 设置系统目录和日志位置。
		SystemConfig.setSystemPath(path);
		Logger.setSystemPath(path);
	
		
		Map<String, String> properties = PropertyUtil.readAsMap(path + "config.properties");
		// 监视目录。
		watchPath = properties.get("watchPath").split(",");
		// 同步目录。
		syncPath = properties.get("syncPath").split(",");
	
		
		// 检查相关目录，如果不存在就立即创建。
		for (int i = 0; i < watchPath.length; i++) {
			String item = watchPath[i];
			File watchPathFile = new File(item);
			if (!watchPathFile.exists()) {
				watchPathFile.mkdirs();
			}
			watchPath[i] = SystemConfig.appendLastFileSeparator(watchPathFile.getAbsolutePath());
		}
		
		for (int i = 0; i < syncPath.length; i++) {
			String item = syncPath[i];
			File asyncPathFile = new File(item);
			if (!asyncPathFile.exists()) {
				asyncPathFile.mkdirs();
			}
			syncPath[i] = SystemConfig.appendLastFileSeparator(asyncPathFile.getAbsolutePath());
		}
		
		
		
		// 差异监听周期。
		watchInterval = NumberUtil.parseInt(StringUtil.unEmpty(properties.get("watchInterval"), DEFAULT_WATCH_INTERVAL + ""));
		
	
		// 排除的文件类型。
		String excludeExtsStr = StringUtil.unNull(properties.get("excludeExts"));
		if (!excludeExtsStr.isEmpty()) {
			for (String item : excludeExtsStr.split(",")) {
				item = item.trim().toLowerCase();
				
				if (!item.isEmpty()) {
					excludeExts.add(item.trim());
				}
			}
		}


		// 排除的文件或文件夹。
		File excludeFilesFile = new File(path + "excludeFiles.txt");
		if (excludeFilesFile.exists()) {
			for (String line : FileUtil.read(excludeFilesFile).split("\n")) {
				// 过滤前后空格。
				line = line.trim();

				// 过滤空行和注释行。
				if (!line.isEmpty() && line.indexOf("#") == -1) {
					for (String item : watchPath) {
						excludeFiles.add(new File(item + "\\" + line).getAbsolutePath());
					}
				}
			}
		}


		// 监视器初始化。
		int size = watchPath.length;
		monitor = new FileAlterationMonitor[size];
		
		for (int i = 0; i < size; i++) {
			monitor[i] = new FileAlterationMonitor(watchInterval * 1000);
			FileAlterationObserver observer = new FileAlterationObserver(new File(watchPath[i]));
			monitor[i].addObserver(observer);
			observer.addListener(new SyncListener(this, i));
		}


		Logger.log("Watch path: " + ArrayUtil.join(watchPath));
		Logger.log("Sync path: " + ArrayUtil.join(syncPath));
		Logger.log("Exclude exts: " + excludeExts);
		Logger.log("Exclude files: " + excludeFiles);
		Logger.log("Inited");
	}


	/**
	 * 是否是排除的。
	 * @param file
	 * 
	 * @return
	 */
	private boolean excluded(File file) {
		
		if (file == null) {
			return true;
		}

		
		if (file.isFile()) {
			String filename = file.getName();
			int lastPointIndex = filename.lastIndexOf(".");
			String ext = "";
			if (lastPointIndex != -1) {
				ext = filename.substring(lastPointIndex).toLowerCase();
			}
	
			// 文件类型的排除判断。
			if (excludeExts.indexOf(ext) != -1) {
				return true;
			}
		}


		String filePath = file.getAbsolutePath();
		int index = excludeFiles.indexOf(filePath);
		if (index != -1) {
			return true;
		} else {
			for (String item : excludeFiles) {
				if (item.startsWith(filePath)) {
					return true;
				}
			}
		}


		return false;
	}
	
	
	/**
	 * 将监视的文件转换成同步路径文件。
	 * 
	 * @param watchFile
	 * @param index
	 * @return
	 */
	private File toSyncFile(File watchFile, int index) {
		
		return new File(watchFile.getAbsolutePath().replace(watchPath[index], syncPath[index]));
	}



	/**
	 * 新建操作。
	 * 
	 * @param file
	 * @param index
	 */
	public void create(File file, int index) {
		
		try {
			if (excluded(file)) {
				return;
			}


			File syncFile = toSyncFile(file, index);


			if (file.isDirectory()) {
				// 新建文件夹。
				if (!syncFile.exists()) {
					syncFile.mkdirs();
				}
			} else {
				// 复制文件。
				FileUtil.save(syncFile, FileUtil.readAsByte(file));
			}
		} catch (Exception e) {
			Logger.printStackTrace(e);
		}
	}



	/**
	 * 修改操作。
	 * 
	 * @param file
	 * @param index
	 */
	public void modify(File file, int index) {
		
		// 同新增操作。
		create(file, index);
	}



	/**
	 * 删除操作。
	 * 
	 * @param file
	 * @param index
	 */
	public void delete(File file, int index) {
		
		try {
			if (excluded(file)) {
				return;
			}
			

			File syncFile = toSyncFile(file, index);


			if (file.isDirectory()) {
				// 删除文件夹。
				if (syncFile.exists()) {
					syncFile.delete();
				}
			} else {
				// 删除文件。
				FileUtil.delete(syncFile);
			}
		} catch (Exception e) {
			Logger.printStackTrace(e);
		}
	}


	/**
	 * 程序入口。
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		
		FileSync fileMonitor = new FileSync();
		fileMonitor.init();
		fileMonitor.start();
	}
}