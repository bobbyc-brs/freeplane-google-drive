package org.freeplane.plugin.googledrive.actions;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.SwingWorker;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.url.mindmapmode.MapLoader;
import org.freeplane.plugin.googledrive.DriveMapTracker;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;
import org.freeplane.plugin.googledrive.ui.GoogleDriveFileBrowser;

public class OpenFromGoogleDriveAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;
	private static final String ACTION_IDENTIFIER = "OpenFromGoogleDriveAction";

	private final GoogleAuthManager authManager;

	public OpenFromGoogleDriveAction(GoogleAuthManager authManager) {
		super(ACTION_IDENTIFIER);
		this.authManager = authManager;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			if (!authManager.isAuthenticated()) {
				authManager.getCredential();
			}

			GoogleDriveClient driveClient = new GoogleDriveClient(
					authManager.getCredential(),
					authManager.getHttpTransport(),
					authManager.getJsonFactory());

			Frame frame = UITools.getCurrentFrame();
			GoogleDriveFileBrowser browser = new GoogleDriveFileBrowser(frame, driveClient);

			if (browser.showDialog()) {
				DriveFile selectedFile = browser.getSelectedFile();
				if (selectedFile != null && selectedFile.isMindMap()) {
					loadMindMapFromDrive(driveClient, selectedFile);
				}
			}
		} catch (IOException ex) {
			LogUtils.warn("Failed to access Google Drive", ex);
			UITools.errorMessage("Failed to access Google Drive: " + ex.getMessage());
		}
	}

	private void loadMindMapFromDrive(GoogleDriveClient driveClient, DriveFile driveFile) {
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
					MapModel map = get();
					if (map != null) {
						String mapName = driveFile.getName();
						if (mapName.toLowerCase().endsWith(".mm")) {
							mapName = mapName.substring(0, mapName.length() - 3);
						}
						map.getRootNode().setText(mapName);
						DriveMapTracker.getInstance().registerMap(map, driveFile);
					}
				} catch (Exception ex) {
					LogUtils.warn("Failed to load map from Google Drive", ex);
					UITools.errorMessage("Failed to load map: " + ex.getMessage());
				}
			}
		};
		worker.execute();
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
