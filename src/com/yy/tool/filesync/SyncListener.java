package com.yy.tool.filesync;

import java.io.File;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;

/**
 * 文件差异监听器。
 * 
 * @since 2018-02-21
 * @version 1.0
 * @author Luowen
 */
public class SyncListener implements FileAlterationListener {

	private FileSync monitor;
	private int index;
	
	
	/**
	 * 构造函数。
	 * 
	 * @param monitor
	 * @param index
	 */
	public SyncListener(FileSync monitor, int index) {
		
		this.monitor = monitor;
		this.index = index;
	}

	
	@Override
	public void onStart(FileAlterationObserver observer) {
		
//		System.out.println("onStart");
	}

	@Override
	public void onDirectoryCreate(File directory) {
		
		monitor.create(directory, index);
	}

	@Override
	public void onDirectoryChange(File directory) {
		
		monitor.modify(directory, index);
	}

	@Override
	public void onDirectoryDelete(File directory) {
		
		monitor.delete(directory, index);
	}

	@Override
	public void onFileCreate(File file) {
	
		monitor.create(file, index);
	}

	@Override
	public void onFileChange(File file) {
		
		monitor.modify(file, index);
	}

	@Override
	public void onFileDelete(File file) {
		
		monitor.delete(file, index);
	}

	@Override
	public void onStop(FileAlterationObserver observer) {
		
//		System.out.println("onStop");
	}
}