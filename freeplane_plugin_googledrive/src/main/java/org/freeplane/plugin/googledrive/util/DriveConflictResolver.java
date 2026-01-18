package org.freeplane.plugin.googledrive.util;

import java.awt.Frame;

import javax.swing.JOptionPane;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;

public class DriveConflictResolver {

	public enum ConflictChoice {
		OVERWRITE_REMOTE,
		RELOAD_FROM_DRIVE,
		SAVE_AS_NEW,
		CANCEL
	}

	public static ConflictChoice showConflictDialog(String fileName) {
		Frame frame = UITools.getCurrentFrame();

		String message = TextUtils.format("googledrive.conflict.message", fileName);
		String title = TextUtils.getText("googledrive.conflict.title");

		String overwriteOption = TextUtils.getText("googledrive.conflict.overwrite");
		String reloadOption = TextUtils.getText("googledrive.conflict.reload");
		String saveAsOption = TextUtils.getText("googledrive.conflict.saveas");
		String cancelOption = TextUtils.getText("googledrive.conflict.cancel");

		Object[] options = { overwriteOption, reloadOption, saveAsOption, cancelOption };

		int result = JOptionPane.showOptionDialog(
				frame,
				message,
				title,
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,
				options,
				cancelOption);

		switch (result) {
		case 0:
			return ConflictChoice.OVERWRITE_REMOTE;
		case 1:
			return ConflictChoice.RELOAD_FROM_DRIVE;
		case 2:
			return ConflictChoice.SAVE_AS_NEW;
		default:
			return ConflictChoice.CANCEL;
		}
	}

}
