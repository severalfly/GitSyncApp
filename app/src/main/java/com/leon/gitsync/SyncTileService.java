package com.leon.gitsync;

import android.annotation.TargetApi;
import android.service.quicksettings.TileService;

/** 下拉快捷设置磁贴：点一下同步全部仓库 */
@TargetApi(24)
public class SyncTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        SyncUtil.startSync(this, false);
    }
}
