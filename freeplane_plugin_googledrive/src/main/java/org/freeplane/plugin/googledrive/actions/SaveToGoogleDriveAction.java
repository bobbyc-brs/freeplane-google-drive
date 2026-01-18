package org.freeplane.plugin.googledrive.actions;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.SwingWorker;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.googledrive.DriveMapTracker;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;
import org.freeplane.plugin.googledrive.ui.GoogleDriveFileBrowser;
import org.freeplane.plugin.googledrive.util.MapSerializer;

public class SaveToGoogleDriveAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;
	private static final String ACTION_IDENTIFIER = "SaveToGoogleDriveAction";

	private final GoogleAuthManager authManager;

	public SaveToGoogleDriveAction(GoogleAuthManager authManager) {
		super(ACTION_IDENTIFIER);
		this.authManager = authManager;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		MapModel map = Controller.getCurrentController().getMap();
		if (map == null) {
			UITools.errorMessage("No map is currently open.");
			return;
		}

		try {
			if (!authManager.isAuthenticated()) {
				authManager.getCredential();
			}

			GoogleDriveClient driveClient = new GoogleDriveClient(
					authManager.getCredential(),
					authManager.getHttpTransport(),
					authManager.getJsonFactory());

			DriveMapTracker tracker = DriveMapTracker.getInstance();
			if (tracker.isFromDrive(map)) {
				updateExistingFile(driveClient, map, tracker.getDriveFile(map));
			} else {
				saveAsNewFile(driveClient, map);
			}
		} catch (IOException ex) {
			LogUtils.warn("Failed to access Google Drive", ex);
			UITools.errorMessage("Failed to access Google Drive: " + ex.getMessage());
		}
	}

	private void saveAsNewFile(GoogleDriveClient driveClient, MapModel map) {
		String defaultFileName = getDefaultFileName(map);

		Frame frame = UITools.getCurrentFrame();
		GoogleDriveFileBrowser browser = new GoogleDriveFileBrowser(frame, driveClient, true, defaultFileName);

		if (browser.showDialog()) {
			DriveFile selectedFolder = browser.getSelectedFolder();
			String fileName = browser.getFileName();

			if (selectedFolder != null && fileName != null && !fileName.isEmpty()) {
				uploadNewFile(driveClient, map, selectedFolder, fileName);
			}
		}
	}

	private String getDefaultFileName(MapModel map) {
		String name = map.getRootNode().getText();
		if (name == null || name.isEmpty()) {
			name = "Untitled";
		}
		name = name.replaceAll("[^a-zA-Z0-9\\-_\\. ]", "_");
		if (!name.toLowerCase().endsWith(".mm")) {
			name = name + ".mm";
		}
		return name;
	}

	void uploadNewFile(GoogleDriveClient driveClient, MapModel map, DriveFile folder, String fileName) {
		Controller controller = Controller.getCurrentController();
		controller.getViewController().setWaitingCursor(true);

		SwingWorker<DriveFile, Void> worker = new SwingWorker<DriveFile, Void>() {
			@Override
			protected DriveFile doInBackground() throws Exception {
				InputStream mapStream = MapSerializer.serializeMapAsStream(map);
				return driveClient.uploadFile(folder.getId(), fileName, mapStream);
			}

			@Override
			protected void done() {
				controller.getViewController().setWaitingCursor(false);
				try {
					DriveFile uploadedFile = get();
					if (uploadedFile != null) {
						DriveMapTracker.getInstance().registerMap(map, uploadedFile);

						MMapController mapController = (MMapController) Controller
								.getCurrentModeController().getMapController();
						mapController.mapSaved(map, true);

						UITools.informationMessage("Map saved to Google Drive as: " + uploadedFile.getName());
					}
				} catch (Exception ex) {
					LogUtils.warn("Failed to save map to Google Drive", ex);
					UITools.errorMessage("Failed to save map: " + ex.getMessage());
				}
			}
		};
		worker.execute();
	}

	private void updateExistingFile(GoogleDriveClient driveClient, MapModel map, DriveFile existingFile) {
		Controller controller = Controller.getCurrentController();
		controller.getViewController().setWaitingCursor(true);

		SwingWorker<DriveFile, Void> worker = new SwingWorker<DriveFile, Void>() {
			@Override
			protected DriveFile doInBackground() throws Exception {
				InputStream mapStream = MapSerializer.serializeMapAsStream(map);
				return driveClient.updateFile(existingFile.getId(), mapStream);
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

						UITools.informationMessage("Map updated on Google Drive: " + updatedFile.getName());
					}
				} catch (Exception ex) {
					LogUtils.warn("Failed to update map on Google Drive", ex);
					UITools.errorMessage("Failed to update map: " + ex.getMessage());
				}
			}
		};
		worker.execute();
	}

}
