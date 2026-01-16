package org.freeplane.plugin.googledrive.auth;

import java.io.File;

import org.freeplane.core.resources.ResourceController;

public class TokenStorage {

	private static final String TOKEN_DIRECTORY_NAME = "googledrive";

	public File getTokenDirectory() {
		String userDir = ResourceController.getResourceController().getFreeplaneUserDirectory();
		File tokenDir = new File(userDir, TOKEN_DIRECTORY_NAME);
		if (!tokenDir.exists()) {
			tokenDir.mkdirs();
			setRestrictivePermissions(tokenDir);
		}
		return tokenDir;
	}

	private void setRestrictivePermissions(File dir) {
		dir.setReadable(false, false);
		dir.setWritable(false, false);
		dir.setExecutable(false, false);
		dir.setReadable(true, true);
		dir.setWritable(true, true);
		dir.setExecutable(true, true);
	}

	public void clearTokens() {
		File tokenDir = getTokenDirectory();
		File[] files = tokenDir.listFiles();
		if (files != null) {
			for (File file : files) {
				file.delete();
			}
		}
	}

	public boolean hasStoredCredentials() {
		File tokenDir = getTokenDirectory();
		File storedCredential = new File(tokenDir, "StoredCredential");
		return storedCredential.exists();
	}

}
