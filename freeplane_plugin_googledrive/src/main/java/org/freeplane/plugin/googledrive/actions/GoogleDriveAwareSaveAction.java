package org.freeplane.plugin.googledrive.actions;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.EnabledAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.url.mindmapmode.MapLoader;
import org.freeplane.plugin.googledrive.DriveMapTracker;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;
import org.freeplane.plugin.googledrive.ui.GoogleDriveFileBrowser;
import org.freeplane.plugin.googledrive.util.DriveConflictResolver;
import org.freeplane.plugin.googledrive.util.DriveConflictResolver.ConflictChoice;
import org.freeplane.plugin.googledrive.util.DriveSaveService;
import org.freeplane.plugin.googledrive.util.DriveSaveService.SaveResult;

@EnabledAction(checkOnNodeChange = true)
public class GoogleDriveAwareSaveAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;
	private static final String ACTION_IDENTIFIER = "SaveAction";

	private final GoogleAuthManager authManager;

	public GoogleDriveAwareSaveAction(GoogleAuthManager authManager) {
		super(ACTION_IDENTIFIER);
		this.authManager = authManager;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		MapModel map = Controller.getCurrentController().getMap();
		if (map == null) {
			return;
		}

		if (map.isReadOnly()) {
			JOptionPane.showMessageDialog(
					Controller.getCurrentController().getMapViewManager().getMapViewComponent(),
					TextUtils.getText("SaveAction_readonlyMsg"),
					TextUtils.getText("SaveAction_readonlyTitle"),
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		DriveMapTracker tracker = DriveMapTracker.getInstance();
		if (tracker.isFromDrive(map)) {
			saveToDrive(map);
		} else {
			saveLocally();
		}
	}

	private void saveToDrive(MapModel map) {
		try {
			if (!authManager.isAuthenticated()) {
				authManager.getCredential();
			}

			GoogleDriveClient driveClient = new GoogleDriveClient(
					authManager.getCredential(),
					authManager.getHttpTransport(),
					authManager.getJsonFactory());

			DriveSaveService.saveToDrive(driveClient, map, result -> handleSaveResult(result, driveClient, map));

		} catch (IOException ex) {
			LogUtils.warn("Failed to access Google Drive", ex);
			UITools.errorMessage("Failed to access Google Drive: " + ex.getMessage());
		}
	}

	private void handleSaveResult(SaveResult result, GoogleDriveClient driveClient, MapModel map) {
		switch (result) {
		case SUCCESS:
			break;
		case CONFLICT:
			handleConflict(driveClient, map);
			break;
		case FAILED:
			break;
		case NO_MAP:
			break;
		}
	}

	private void handleConflict(GoogleDriveClient driveClient, MapModel map) {
		DriveFile driveFile = DriveMapTracker.getInstance().getDriveFile(map);
		String fileName = driveFile != null ? driveFile.getName() : "unknown";

		ConflictChoice choice = DriveConflictResolver.showConflictDialog(fileName);

		switch (choice) {
		case OVERWRITE_REMOTE:
			DriveSaveService.forceSaveToDrive(driveClient, map, r -> {
			});
			break;
		case RELOAD_FROM_DRIVE:
			reloadFromDrive(driveClient, map, driveFile);
			break;
		case SAVE_AS_NEW:
			saveAsNewToDrive(driveClient, map);
			break;
		case CANCEL:
			break;
		}
	}

	private void reloadFromDrive(GoogleDriveClient driveClient, MapModel map, DriveFile driveFile) {
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

	private void saveAsNewToDrive(GoogleDriveClient driveClient, MapModel map) {
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

	private void saveLocally() {
		boolean success = ((MModeController) Controller.getCurrentModeController()).save();
		Controller controller = Controller.getCurrentController();
		if (success) {
			controller.getViewController().out(TextUtils.getText("saved"));
		} else {
			controller.getViewController().out(TextUtils.getText("saving_canceled"));
		}
	}

	@Override
	public void setEnabled() {
		Controller controller = Controller.getCurrentController();
		MapModel map = controller.getMap();
		setEnabled(map != null && !map.isSaved());
	}

}
