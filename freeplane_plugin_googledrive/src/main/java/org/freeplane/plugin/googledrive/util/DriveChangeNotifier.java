package org.freeplane.plugin.googledrive.util;

import java.awt.Frame;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.url.mindmapmode.MapLoader;
import org.freeplane.plugin.googledrive.DriveMapTracker;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;

public class DriveChangeNotifier implements DriveChangeMonitor.ChangeListener {

	private final GoogleAuthManager authManager;

	public DriveChangeNotifier(GoogleAuthManager authManager) {
		this.authManager = authManager;
	}

	@Override
	public void onRemoteChangeDetected(MapModel map, DriveFile driveFile) {
		Frame frame = UITools.getCurrentFrame();
		if (frame == null || !frame.isShowing()) {
			return;
		}

		String message = TextUtils.format("googledrive.remote_change.message", driveFile.getName());
		String title = TextUtils.getText("googledrive.remote_change.title");

		String reloadOption = TextUtils.getText("googledrive.remote_change.reload");
		String ignoreOption = TextUtils.getText("googledrive.remote_change.ignore");

		Object[] options = { reloadOption, ignoreOption };

		int result = JOptionPane.showOptionDialog(
				frame,
				message,
				title,
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.INFORMATION_MESSAGE,
				null,
				options,
				reloadOption);

		if (result == 0) {
			reloadFromDrive(map, driveFile);
		} else {
			DriveMapTracker.getInstance().updateLastKnownModifiedTime(map, driveFile.getModifiedTime());
		}
	}

	private void reloadFromDrive(MapModel map, DriveFile driveFile) {
		try {
			if (!authManager.isAuthenticated()) {
				authManager.getCredential();
			}

			GoogleDriveClient driveClient = new GoogleDriveClient(
					authManager.getCredential(),
					authManager.getHttpTransport(),
					authManager.getJsonFactory());

			Controller controller = Controller.getCurrentController();
			controller.getViewController().setWaitingCursor(true);

			SwingWorker<MapModel, Void> worker = new SwingWorker<MapModel, Void>() {
				@Override
				protected MapModel doInBackground() throws Exception {
					File tempFile = downloadToTempFile(driveClient, driveFile);

					ModeController modeController = Controller.getCurrentModeController();
					MapLoader loader = new MapLoader(modeController);

					return loader
							.load(tempFile)
							.unsetMapLocation()
							.withView()
							.getMap();
				}

				@Override
				protected void done() {
					controller.getViewController().setWaitingCursor(false);
					try {
						MapModel newMap = get();
						if (newMap != null) {
							DriveFile refreshedFile = driveClient.getFileMetadata(driveFile.getId());
							DriveMapTracker.getInstance().registerMap(newMap, refreshedFile);

							String mapName = driveFile.getName();
							if (mapName.toLowerCase().endsWith(".mm")) {
								mapName = mapName.substring(0, mapName.length() - 3);
							}
							newMap.getRootNode().setText(mapName);

							controller.getViewController().out("Reloaded from Google Drive: " + driveFile.getName());
						}
					} catch (Exception ex) {
						LogUtils.warn("Failed to reload map from Google Drive", ex);
						UITools.errorMessage("Failed to reload map: " + ex.getMessage());
					}
				}
			};
			worker.execute();

		} catch (IOException e) {
			LogUtils.warn("Failed to access Google Drive for reload", e);
			UITools.errorMessage("Failed to access Google Drive: " + e.getMessage());
		}
	}

	private File downloadToTempFile(GoogleDriveClient driveClient, DriveFile driveFile) throws IOException {
		File tempFile = File.createTempFile("freeplane_gdrive_", ".mm");
		tempFile.deleteOnExit();

		try (InputStream in = driveClient.downloadFile(driveFile.getId());
				OutputStream out = new FileOutputStream(tempFile)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		}

		return tempFile;
	}

}
