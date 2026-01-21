package org.freeplane.plugin.googledrive.actions;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.IOException;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.url.mindmapmode.MFileManager;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;
import org.freeplane.plugin.googledrive.ui.GoogleDriveFileBrowser;
import org.freeplane.plugin.googledrive.util.DriveSaveService;

public class NewMapToGoogleDriveAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;
	private static final String ACTION_IDENTIFIER = "NewMapToGoogleDriveAction";

	private final GoogleAuthManager authManager;

	public NewMapToGoogleDriveAction(GoogleAuthManager authManager) {
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
			GoogleDriveFileBrowser browser = new GoogleDriveFileBrowser(frame, driveClient, true, "New Mind Map.mm");

			if (browser.showDialog()) {
				DriveFile selectedFolder = browser.getSelectedFolder();
				String fileName = browser.getFileName();

				if (selectedFolder != null && fileName != null && !fileName.isEmpty()) {
					createAndSaveNewMap(driveClient, selectedFolder, fileName);
				}
			}
		} catch (IOException ex) {
			LogUtils.warn("Failed to access Google Drive", ex);
			UITools.errorMessage("Failed to access Google Drive: " + ex.getMessage());
		}
	}

	private void createAndSaveNewMap(GoogleDriveClient driveClient, DriveFile folder, String fileName) {
		ModeController modeController = Controller.getCurrentController().getModeController(MModeController.MODENAME);
		MapModel map = MFileManager.getController(modeController).newMapFromDefaultTemplate();

		if (map != null) {
			String mapName = fileName;
			if (mapName.toLowerCase().endsWith(".mm")) {
				mapName = mapName.substring(0, mapName.length() - 3);
			}
			map.getRootNode().setText(mapName);

			DriveSaveService.uploadNewFile(driveClient, map, folder, fileName, result -> {});
		}
	}

}
