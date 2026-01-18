package org.freeplane.plugin.googledrive.util;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.googledrive.DriveMapTracker;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;

public class DriveChangeMonitor {

	public interface ChangeListener {
		void onRemoteChangeDetected(MapModel map, DriveFile driveFile);
	}

	private static final long DEFAULT_POLL_INTERVAL_SECONDS = 5;

	private final GoogleAuthManager authManager;
	private final ScheduledExecutorService scheduler;
	private ScheduledFuture<?> pollingTask;
	private ChangeListener changeListener;
	private long pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS;

	public DriveChangeMonitor(GoogleAuthManager authManager) {
		this.authManager = authManager;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "DriveChangeMonitor");
			t.setDaemon(true);
			return t;
		});
	}

	public void setChangeListener(ChangeListener listener) {
		this.changeListener = listener;
	}

	public void setPollIntervalSeconds(long seconds) {
		this.pollIntervalSeconds = seconds;
		if (pollingTask != null) {
			stop();
			start();
		}
	}

	public void start() {
		if (pollingTask != null && !pollingTask.isCancelled()) {
			return;
		}

		pollingTask = scheduler.scheduleWithFixedDelay(
				this::checkForChanges,
				pollIntervalSeconds,
				pollIntervalSeconds,
				TimeUnit.SECONDS);

		LogUtils.info("DriveChangeMonitor started with " + pollIntervalSeconds + "s interval");
	}

	public void stop() {
		if (pollingTask != null) {
			pollingTask.cancel(false);
			pollingTask = null;
			LogUtils.info("DriveChangeMonitor stopped");
		}
	}

	public void shutdown() {
		stop();
		scheduler.shutdown();
	}

	public void checkNow() {
		scheduler.execute(this::checkForChanges);
	}

	private void checkForChanges() {
		if (!authManager.isAuthenticated()) {
			return;
		}

		DriveMapTracker tracker = DriveMapTracker.getInstance();
		Set<MapModel> trackedMaps = tracker.getTrackedMaps();

		if (trackedMaps.isEmpty()) {
			return;
		}

		GoogleDriveClient client;
		try {
			client = new GoogleDriveClient(
					authManager.getCredential(),
					authManager.getHttpTransport(),
					authManager.getJsonFactory());
		} catch (IOException e) {
			LogUtils.warn("Failed to create Drive client for change monitoring", e);
			return;
		}

		for (MapModel map : trackedMaps) {
			checkMapForChanges(client, tracker, map);
		}
	}

	private void checkMapForChanges(GoogleDriveClient client, DriveMapTracker tracker, MapModel map) {
		DriveFile driveFile = tracker.getDriveFile(map);
		if (driveFile == null) {
			return;
		}

		String lastKnownTime = tracker.getLastKnownModifiedTime(map);
		if (lastKnownTime == null) {
			return;
		}

		try {
			DriveFile currentMetadata = client.getFileMetadata(driveFile.getId());
			String remoteTime = currentMetadata.getModifiedTime();

			if (remoteTime != null && !remoteTime.equals(lastKnownTime)) {
				notifyChange(map, currentMetadata);
			}
		} catch (IOException e) {
			LogUtils.warn("Failed to check remote changes for: " + driveFile.getName(), e);
		}
	}

	private void notifyChange(MapModel map, DriveFile driveFile) {
		if (changeListener != null) {
			SwingUtilities.invokeLater(() -> changeListener.onRemoteChangeDetected(map, driveFile));
		}
	}

}
