/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.launcher.core.asset;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftAssetService;
import org.jackhuang.hellominecraft.launcher.core.download.IDownloadProvider;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.util.code.DigestUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;
import org.jackhuang.hellominecraft.util.NetUtils;
import org.jackhuang.hellominecraft.util.OverridableSwingWorker;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.func.Function;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;

/**
 * Assets
 *
 * @author huangyuhui
 */
public abstract class IAssetsHandler {

    protected ArrayList<String> assetsDownloadURLs;
    protected ArrayList<File> assetsLocalNames;
    protected final String name;
    protected List<Contents> contents;

    public IAssetsHandler(String name) {
        this.name = name;
    }

    public static final IAssetsHandler ASSETS_HANDLER;

    static {
        ASSETS_HANDLER = new AssetsMojangLoader(C.i18n("assets.list.1_7_3_after"));
    }

    /**
     * interface name
     *
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * All the files assets needed
     *
     * @param mv The version that needs assets
     * @param mp Asset Service
     * @param x  finished event
     */
    public abstract Task getList(MinecraftVersion mv, IMinecraftAssetService mp);

    /**
     * Will be invoked when the user invoked "Download all assets".
     *
     * @param sourceType Download Source
     *
     * @return Download File Task
     */
    public abstract Task getDownloadTask(IDownloadProvider sourceType);

    public abstract boolean isVersionAllowed(String formattedVersion);

    protected class AssetsTask extends TaskInfo {

        ArrayList<Task> tasks;
        String baseUrl;
		String retryBaseUrl;

        public AssetsTask(String url, String retryUrl) {
            super(C.i18n("assets.download"));
            this.baseUrl = url;
			this.retryBaseUrl = retryUrl;
        }

        @Override
        public void executeTask() {
            if (assetsDownloadURLs == null || assetsLocalNames == null || contents == null)
                throw new IllegalStateException(C.i18n("assets.not_refreshed"));
			
			tasks = new ArrayList<>();
            int max = assetsDownloadURLs.size();
            int hasDownloaded = 0;
            for (int i = 0; i < max; i++) {
                String mark = assetsDownloadURLs.get(i);
                String downloadUrl = baseUrl + mark;
				String downloadRetryUrl = StrUtils.isNotBlank(retryBaseUrl) ? retryBaseUrl + mark : ""; 
                File location = assetsLocalNames.get(i);
				
                if (!location.getParentFile().exists() && !location.getParentFile().mkdirs())
                    HMCLog.warn("Failed to make directories: " + location.getParent());
				
                if (location.isDirectory())
                    continue;
				
                boolean needDownload = true;
                try {
                    if (location.exists()) {
                        FileInputStream fis = new FileInputStream(location);
                        String sha = DigestUtils.sha1Hex(NetUtils.getBytesFromStream(fis));
                        IOUtils.closeQuietly(fis);
                        if (contents.get(i).geteTag().equals(sha)) {
                            ++hasDownloaded;
                            HMCLog.log("File " + assetsLocalNames.get(i) + " has been downloaded successfully, skipped downloading.");
                            if (ppl != null) {
                                ppl.setProgress(this, hasDownloaded, max);
							}
                            continue;
                        }
                    }
                } catch (IOException e) {
                    HMCLog.warn("Failed to get hash: " + location, e);
                    needDownload = !location.exists();
                }
				
                if (needDownload) {
					FileDownloadTask fileDownloadTask = new FileDownloadTask(downloadUrl, location);
					fileDownloadTask.setTag(mark);
					
					// retry
					if (StrUtils.isNotBlank(downloadRetryUrl)) {
						fileDownloadTask.setFailedCallbackReturnsNewURL(new Function<Integer, String>() {
							@Override
							public String apply(Integer t) {
								return downloadRetryUrl;
							}
						});
					}

                    tasks.add(fileDownloadTask);
				}
            }
        }

        @Override
        public Collection<Task> getAfterTasks() {
            return tasks;
        }
    }
}
