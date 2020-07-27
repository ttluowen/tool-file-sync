package com.yy.tool.filesync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yy.java.config.JavaConfig;
import com.yy.log.Logger;
import com.yy.util.FileUtil;
import com.yy.util.JsonConfigUtil;
import com.yy.util.NumberUtil;
import com.yy.util.StringUtil;

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

	/** 监听的目录。 */
	private List<PathStruct> paths = new ArrayList<>();

	/** 差异监听周期，单位秒。 */
	private int interval = DEFAULT_WATCH_INTERVAL;

	/** 排除的文件类型。 */
	private List<String> excludeExts = new ArrayList<>();

	/** 排除的文件或文件夹列表。 */
	private List<String> excludeFiles = new ArrayList<>();

	public static final String LISTEN_CREATE = "create";
	public static final String LISTEN_CHANGE = "change";
	public static final String LISTEN_DELETE = "delete";

	/** 监听的动作类型。 */
	private List<String> listens = new ArrayList<>();

	/** 文件变化监视器。 */
	private FileAlterationMonitor[] monitor;

	/**
	 * 启动监视器。
	 * 
	 * @throws Exception
	 */
	public void stop() throws Exception {

		if (monitor != null) {
			for (FileAlterationMonitor item : monitor) {
				item.stop();
			}
		}
	}

	/**
	 * 关闭监视器。
	 * 
	 * @throws Exception
	 */
	public void start() throws Exception {

		if (monitor != null) {
			for (FileAlterationMonitor item : monitor) {
				item.start();
			}
		}
	}

	/**
	 * 系统信息初始化。
	 * 
	 * @param second
	 * @throws IOException
	 */
	private void init() throws IOException {

		JSONObject config = JsonConfigUtil.read(JavaConfig.getSystemPath() + "config.json");
		if (config == null) {
			Logger.log("读取配置失败");
			return;
		}

		JSONArray paths = config.getJSONArray("paths");
		for (int i = 0, l = paths.size(); i < l; i++) {
			JSONObject item = paths.getJSONObject(i);
			String watch = item.getString("watch");
			JSONArray syncs = item.getJSONArray("syncs");
			List<String> syncList = new ArrayList<>();

			for (int a = 0; a < syncs.size(); a++) {
				syncList.add(syncs.getString(a));
			}

			// 文件是否存在检测。
			File watchPathFile = new File(watch);
			if (!watchPathFile.exists()) {
				watchPathFile.mkdirs();
			}
			watch = JavaConfig.appendLastFileSeparator(watchPathFile.getAbsolutePath());

			for (int a = 0; a < syncList.size(); a++) {
				String sy = syncList.get(a);
				File asyncPathFile = new File(sy);
				if (!asyncPathFile.exists()) {
					asyncPathFile.mkdirs();
				}
				syncList.set(a, JavaConfig.appendLastFileSeparator(asyncPathFile.getAbsolutePath()));
			}

			PathStruct path = new PathStruct();
			path.setWatch(watch);
			path.setSync(syncList);

			this.paths.add(path);
		}

		// 差异监听周期。
		interval = NumberUtil.parseInt(StringUtil.unEmpty(config.getString("interval"), DEFAULT_WATCH_INTERVAL + ""));

		// 监听的动作。
		List<Object> listensArray = config.getJSONArray("listens");
		if (listensArray != null) {
			for (Object item : listensArray) {
				String str = StringUtil.unNull(item).trim();
				if (!StringUtil.isEmpty(str)) {
					listens.add(str);
				}
			}
		}

		// 排除的文件类型。
		JSONArray excludeExts = config.getJSONArray("excludeExts");
		for (int i = 0; i < excludeExts.size(); i++) {
			this.excludeExts.add(excludeExts.getString(i).trim().toLowerCase());
		}

		// 排除的文件或文件夹。
		File excludeFilesFile = new File(JavaConfig.getSystemPath() + "excludeFiles.txt");
		if (excludeFilesFile.exists()) {
			for (String line : FileUtil.read(excludeFilesFile).split("\n")) {
				// 过滤前后空格。
				line = line.trim();

				// 过滤空行和注释行。
				if (!line.isEmpty() && line.indexOf("#") == -1) {
					for (PathStruct p : this.paths) {
						String watch = p.getWatch();
						excludeFiles.add(new File(watch + "\\" + line).getAbsolutePath());
					}
				}
			}
		}

		// 监视器初始化。
		int size = this.paths.size();
		monitor = new FileAlterationMonitor[size];

		for (int i = 0; i < size; i++) {
			monitor[i] = new FileAlterationMonitor(interval * 1000);
			FileAlterationObserver observer = new FileAlterationObserver(new File(this.paths.get(i).getWatch()));
			monitor[i].addObserver(observer);
			observer.addListener(new SyncListener(this, i));
		}

		for (PathStruct p : this.paths) {
			Logger.log("Watch path: " + p.getWatch());
			Logger.log("Sync path: " + p.getSync());
		}
		Logger.log("Exclude exts: " + excludeExts);
		Logger.log("Exclude files: " + excludeFiles);
		Logger.log("Inited");
	}

	/**
	 * 是否是排除的。
	 * 
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
	private List<File> toSyncFiles(File watchFile, int index) {

		String watchFilePath = watchFile.getAbsolutePath();
		PathStruct path = paths.get(index);
		String watch = path.getWatch();
		List<String> sync = path.getSync();

		List<File> list = new ArrayList<>();
		for (String item : sync) {
			list.add(new File(watchFilePath.replace(watch, item)));
		}

		return list;
	}

	/**
	 * 新建操作。
	 * 
	 * @param file
	 * @param index
	 */
	public void create(File file, int index) {

		if (listens.indexOf(LISTEN_CREATE) == -1) {
			return;
		}

		try {
			if (excluded(file)) {
				return;
			}

			List<File> syncFile = toSyncFiles(file, index);

			if (file.isDirectory()) {
				// 新建文件夹。
				for (File f : syncFile) {
					if (!f.exists()) {
						Logger.log("新建文件夹：" + f);
						f.mkdirs();
					}
				}
			} else {
				// 复制文件。
				for (File f : syncFile) {
					Logger.log("复制文件：" + f);
					FileUtil.save(f, FileUtil.readAsByte(file));
				}
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
	public void change(File file, int index) {

		if (listens.indexOf(LISTEN_CHANGE) == -1) {
			return;
		}

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

		if (listens.indexOf(LISTEN_DELETE) == -1) {
			return;
		}

		try {
			if (excluded(file)) {
				return;
			}

			List<File> syncFile = toSyncFiles(file, index);

			if (file.isDirectory()) {
				// 删除文件夹。
				for (File f : syncFile) {
					if (f.exists()) {
						Logger.log("删除文件夹：" + f);
						f.delete();
					}
				}
			} else {
				// 删除文件。
				for (File f : syncFile) {
					Logger.log("删除文件：" + f);
					FileUtil.delete(f);
				}
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

		JavaConfig.javaInit();

		FileSync fileMonitor = new FileSync();
		fileMonitor.init();
		fileMonitor.start();
	}
}