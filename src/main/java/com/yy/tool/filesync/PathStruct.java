package com.yy.tool.filesync;

import java.util.List;

/**
 * @author Luowen
 * @since 2018-02-21
 */
public class PathStruct {

    private String watch;
    private List<String> sync;

    public String getWatch() {
        return watch;
    }

    public void setWatch(String watch) {
        this.watch = watch;
    }

    public List<String> getSync() {
        return sync;
    }

    public void setSync(List<String> sync) {
        this.sync = sync;
    }
}
