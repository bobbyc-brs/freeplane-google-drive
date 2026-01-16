package org.freeplane.plugin.googledrive.auth;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;

public class GoogleAuthManager {

	private static final String CREDENTIALS_FILE_PATH = "/org/freeplane/plugin/googledrive/credentials.json";
	private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_READONLY);

	private final TokenStorage tokenStorage;
	private final NetHttpTransport httpTransport;
	private final GsonFactory jsonFactory;
	private Credential cachedCredential;

	public GoogleAuthManager(TokenStorage tokenStorage) {
		this.tokenStorage = tokenStorage;
		this.httpTransport = new NetHttpTransport();
		this.jsonFactory = GsonFactory.getDefaultInstance();
	}

	public Credential getCredential() throws IOException {
		if (cachedCredential != null && !isCredentialExpired(cachedCredential)) {
			return cachedCredential;
		}

		GoogleClientSecrets clientSecrets = loadClientSecrets();
		File tokenDir = tokenStorage.getTokenDirectory();

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, jsonFactory, clientSecrets, SCOPES)
				.setDataStoreFactory(new FileDataStoreFactory(tokenDir))
				.setAccessType("offline")
				.build();

		Credential credential = flow.loadCredential("user");

		if (credential == null || credential.getRefreshToken() == null) {
			credential = authorizeWithBrowser(flow);
		} else if (isCredentialExpired(credential)) {
			credential.refreshToken();
		}

		cachedCredential = credential;
		return credential;
	}

	private Credential authorizeWithBrowser(GoogleAuthorizationCodeFlow flow) throws IOException {
		LocalServerReceiver receiver = new LocalServerReceiver.Builder()
				.setPort(-1)
				.build();

		return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
	}

	public boolean isAuthenticated() {
		if (cachedCredential != null && !isCredentialExpired(cachedCredential)) {
			return true;
		}

		if (!tokenStorage.hasStoredCredentials()) {
			return false;
		}

		try {
			Credential credential = getCredential();
			return credential != null && credential.getAccessToken() != null;
		} catch (IOException e) {
			return false;
		}
	}

	public void logout() {
		tokenStorage.clearTokens();
		cachedCredential = null;
	}

	private boolean isCredentialExpired(Credential credential) {
		Long expirationTime = credential.getExpirationTimeMilliseconds();
		return expirationTime != null && expirationTime <= System.currentTimeMillis();
	}

	private GoogleClientSecrets loadClientSecrets() throws IOException {
		InputStream in = GoogleAuthManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
		if (in == null) {
			throw new IOException(
					"Google Drive credentials not found. Please provide credentials.json in the plugin resources.");
		}
		return GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));
	}

	public NetHttpTransport getHttpTransport() {
		return httpTransport;
	}

	public GsonFactory getJsonFactory() {
		return jsonFactory;
	}

}
