package org.freeplane.plugin.googledrive.actions;

import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.googledrive.auth.GoogleAuthManager;

public class LogoutFromGoogleDriveAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;
	private static final String ACTION_IDENTIFIER = "LogoutFromGoogleDriveAction";

	private final GoogleAuthManager authManager;

	public LogoutFromGoogleDriveAction(GoogleAuthManager authManager) {
		super(ACTION_IDENTIFIER);
		this.authManager = authManager;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (!authManager.isAuthenticated()) {
			UITools.informationMessage(TextUtils.getText("googledrive.logout.not_logged_in"));
			return;
		}

		String message = TextUtils.getText("googledrive.logout.confirm_message");
		String title = TextUtils.getText("googledrive.logout.confirm_title");

		int result = JOptionPane.showConfirmDialog(
				UITools.getCurrentFrame(),
				message,
				title,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);

		if (result == JOptionPane.YES_OPTION) {
			authManager.logout();
			UITools.informationMessage(TextUtils.getText("googledrive.logout.success"));
		}
	}

}
