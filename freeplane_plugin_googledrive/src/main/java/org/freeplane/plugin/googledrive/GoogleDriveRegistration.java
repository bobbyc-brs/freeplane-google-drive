package org.freeplane.plugin.googledrive;

import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.googledrive.actions.GoogleDriveAwareSaveAction;
import org.freeplane.plugin.googledrive.actions.LogoutFromGoogleDriveAction;
import org.freeplane.plugin.googledrive.actions.NewMapToGoogleDriveAction;
import org.freeplane.plugin.googledrive.actions.OpenFromGoogleDriveAction;
import org.freeplane.plugin.googledrive.actions.SaveAsToGoogleDriveAction;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;
import org.freeplane.plugin.googledrive.auth.TokenStorage;
import org.freeplane.plugin.googledrive.util.DriveChangeMonitor;
import org.freeplane.plugin.googledrive.util.DriveChangeNotifier;

public class GoogleDriveRegistration {

	private static DriveChangeMonitor changeMonitor;

	public GoogleDriveRegistration(ModeController modeController) {
		if ("MindMap".equals(modeController.getModeName())) {
			TokenStorage tokenStorage = new TokenStorage();
			GoogleAuthManager authManager = new GoogleAuthManager(tokenStorage);

			modeController.addAction(new NewMapToGoogleDriveAction(authManager));
			modeController.addAction(new OpenFromGoogleDriveAction(authManager));
			modeController.addAction(new SaveAsToGoogleDriveAction(authManager));
			modeController.addAction(new LogoutFromGoogleDriveAction(authManager));

			modeController.removeActionIfSet("SaveAction");
			modeController.addAction(new GoogleDriveAwareSaveAction(authManager));

			initializeChangeMonitor(authManager);
		}
	}

	private void initializeChangeMonitor(GoogleAuthManager authManager) {
		changeMonitor = new DriveChangeMonitor(authManager);
		changeMonitor.setChangeListener(new DriveChangeNotifier(authManager));
		changeMonitor.start();
	}

	public static DriveChangeMonitor getChangeMonitor() {
		return changeMonitor;
	}

}
