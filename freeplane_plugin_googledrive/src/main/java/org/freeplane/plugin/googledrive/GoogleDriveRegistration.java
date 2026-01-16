package org.freeplane.plugin.googledrive;

import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.googledrive.actions.OpenFromGoogleDriveAction;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;
import org.freeplane.plugin.googledrive.auth.TokenStorage;

public class GoogleDriveRegistration {

	public GoogleDriveRegistration(ModeController modeController) {
		if ("MindMap".equals(modeController.getModeName())) {
			TokenStorage tokenStorage = new TokenStorage();
			GoogleAuthManager authManager = new GoogleAuthManager(tokenStorage);

			modeController.addAction(new OpenFromGoogleDriveAction(authManager));
		}
	}

}
