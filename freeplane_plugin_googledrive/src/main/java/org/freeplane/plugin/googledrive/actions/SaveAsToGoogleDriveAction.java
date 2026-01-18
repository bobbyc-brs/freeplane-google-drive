package org.freeplane.plugin.googledrive.actions;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.IOException;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;
import org.freeplane.plugin.googledrive.ui.GoogleDriveFileBrowser;

public class SaveAsToGoogleDriveAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;
	private static final String ACTION_IDENTIFIER = "SaveAsToGoogleDriveAction";

	private final GoogleAuthManager authManager;

	public SaveAsToGoogleDriveAction(GoogleAuthManager authManager) {
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

			String defaultFileName = getDefaultFileName(map);

			Frame frame = UITools.getCurrentFrame();
			GoogleDriveFileBrowser browser = new GoogleDriveFileBrowser(frame, driveClient, true, defaultFileName);

			if (browser.showDialog()) {
				DriveFile selectedFolder = browser.getSelectedFolder();
				String fileName = browser.getFileName();

				if (selectedFolder != null && fileName != null && !fileName.isEmpty()) {
					new SaveToGoogleDriveAction(authManager).uploadNewFile(driveClient, map, selectedFolder, fileName);
				}
			}
		} catch (IOException ex) {
			LogUtils.warn("Failed to access Google Drive", ex);
			UITools.errorMessage("Failed to access Google Drive: " + ex.getMessage());
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

}
