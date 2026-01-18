package org.freeplane.plugin.googledrive.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.googledrive.DriveMapTracker;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;

public class DriveSaveService {

	public enum SaveResult {
		SUCCESS,
		CONFLICT,
		FAILED,
		NO_MAP
	}

	public static boolean hasRemoteChanges(GoogleDriveClient client, DriveFile file, String lastKnownModifiedTime) {
		if (lastKnownModifiedTime == null) {
			return false;
		}
		try {
			DriveFile currentMetadata = client.getFileMetadata(file.getId());
			String remoteTime = currentMetadata.getModifiedTime();
			return remoteTime != null && !remoteTime.equals(lastKnownModifiedTime);
		} catch (IOException e) {
			LogUtils.warn("Failed to check remote file metadata", e);
			return false;
		}
	}

	public static void saveToDrive(GoogleDriveClient client, MapModel map, Consumer<SaveResult> onComplete) {
		if (map == null) {
			onComplete.accept(SaveResult.NO_MAP);
			return;
		}

		DriveMapTracker tracker = DriveMapTracker.getInstance();
		DriveFile driveFile = tracker.getDriveFile(map);

		if (driveFile == null) {
			onComplete.accept(SaveResult.FAILED);
			return;
		}

		String lastKnownTime = tracker.getLastKnownModifiedTime(map);
		if (hasRemoteChanges(client, driveFile, lastKnownTime)) {
			onComplete.accept(SaveResult.CONFLICT);
			return;
		}

		executeUpdate(client, map, driveFile, onComplete);
	}

	public static void forceSaveToDrive(GoogleDriveClient client, MapModel map, Consumer<SaveResult> onComplete) {
		if (map == null) {
			onComplete.accept(SaveResult.NO_MAP);
			return;
		}

		DriveMapTracker tracker = DriveMapTracker.getInstance();
		DriveFile driveFile = tracker.getDriveFile(map);

		if (driveFile == null) {
			onComplete.accept(SaveResult.FAILED);
			return;
		}

		executeUpdate(client, map, driveFile, onComplete);
	}

	private static void executeUpdate(GoogleDriveClient client, MapModel map, DriveFile driveFile,
			Consumer<SaveResult> onComplete) {
		Controller controller = Controller.getCurrentController();
		controller.getViewController().setWaitingCursor(true);

		SwingWorker<DriveFile, Void> worker = new SwingWorker<DriveFile, Void>() {
			@Override
			protected DriveFile doInBackground() throws Exception {
				InputStream mapStream = MapSerializer.serializeMapAsStream(map);
				return client.updateFile(driveFile.getId(), mapStream);
			}

			@Override
			protected void done() {
				controller.getViewController().setWaitingCursor(false);
				try {
					DriveFile updatedFile = get();
					if (updatedFile != null) {
						DriveMapTracker.getInstance().updateDriveFile(map, updatedFile);

						MMapController mapController = (MMapController) Controller
								.getCurrentModeController().getMapController();
						mapController.mapSaved(map, true);

						controller.getViewController().out("Saved to Google Drive: " + updatedFile.getName());
						onComplete.accept(SaveResult.SUCCESS);
					} else {
						onComplete.accept(SaveResult.FAILED);
					}
				} catch (Exception ex) {
					LogUtils.warn("Failed to save map to Google Drive", ex);
					UITools.errorMessage("Failed to save map: " + ex.getMessage());
					onComplete.accept(SaveResult.FAILED);
				}
			}
		};
		worker.execute();
	}

}
