package org.freeplane.plugin.googledrive.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class GoogleDriveClient {

	private static final String APPLICATION_NAME = "Freeplane Google Drive Plugin";
	private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
	private static final String FIELDS = "files(id, name, mimeType, size, modifiedTime)";

	private final Drive driveService;

	public GoogleDriveClient(Credential credential, NetHttpTransport httpTransport, GsonFactory jsonFactory) {
		this.driveService = new Drive.Builder(httpTransport, jsonFactory, credential)
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	public List<DriveFile> listFiles(String folderId) throws IOException {
		String query = String.format("'%s' in parents and trashed = false", folderId);

		FileList result = driveService.files().list()
				.setQ(query)
				.setFields(FIELDS)
				.setOrderBy("folder,name")
				.setPageSize(100)
				.execute();

		return convertTodriveFiles(result.getFiles());
	}

	public List<DriveFile> listAllFiles(String folderId) throws IOException {
		List<DriveFile> allFiles = new ArrayList<>();
		String pageToken = null;
		String query = String.format("'%s' in parents and trashed = false", folderId);

		do {
			FileList result = driveService.files().list()
					.setQ(query)
					.setFields("nextPageToken, " + FIELDS)
					.setOrderBy("folder,name")
					.setPageSize(100)
					.setPageToken(pageToken)
					.execute();

			allFiles.addAll(convertTodriveFiles(result.getFiles()));
			pageToken = result.getNextPageToken();
		} while (pageToken != null);

		return allFiles;
	}

	public List<DriveFile> searchMindMaps(String searchTerm) throws IOException {
		String query = String.format(
				"name contains '%s' and name contains '.mm' and mimeType != '%s' and trashed = false",
				escapeQueryString(searchTerm), FOLDER_MIME_TYPE);

		FileList result = driveService.files().list()
				.setQ(query)
				.setFields(FIELDS)
				.setPageSize(50)
				.execute();

		return convertTodriveFiles(result.getFiles());
	}

	public InputStream downloadFile(String fileId) throws IOException {
		return driveService.files().get(fileId)
				.executeMediaAsInputStream();
	}

	public DriveFile uploadFile(String folderId, String fileName, InputStream content) throws IOException {
		File fileMetadata = new File();
		fileMetadata.setName(fileName);
		fileMetadata.setParents(Collections.singletonList(folderId));

		InputStreamContent mediaContent = new InputStreamContent("application/x-freemind", content);

		File file = driveService.files().create(fileMetadata, mediaContent)
				.setFields("id, name, mimeType, size, modifiedTime")
				.execute();

		return toDriveFile(file);
	}

	public DriveFile updateFile(String fileId, InputStream content) throws IOException {
		InputStreamContent mediaContent = new InputStreamContent("application/x-freemind", content);

		File file = driveService.files().update(fileId, null, mediaContent)
				.setFields("id, name, mimeType, size, modifiedTime")
				.execute();

		return toDriveFile(file);
	}

	public DriveFile createFolder(String parentId, String folderName) throws IOException {
		File fileMetadata = new File();
		fileMetadata.setName(folderName);
		fileMetadata.setMimeType(FOLDER_MIME_TYPE);
		if (parentId != null) {
			fileMetadata.setParents(Collections.singletonList(parentId));
		}

		File file = driveService.files().create(fileMetadata)
				.setFields("id, name, mimeType, size, modifiedTime")
				.execute();

		return toDriveFile(file);
	}

	public DriveFile getFile(String fileId) throws IOException {
		File file = driveService.files().get(fileId)
				.setFields("id, name, mimeType, size, modifiedTime")
				.execute();
		return toDriveFile(file);
	}

	public DriveFile getFileMetadata(String fileId) throws IOException {
		File file = driveService.files().get(fileId)
				.setFields("id, name, modifiedTime")
				.execute();
		return toDriveFile(file);
	}

	private List<DriveFile> convertTodriveFiles(List<File> files) {
		List<DriveFile> driveFiles = new ArrayList<>();
		if (files != null) {
			for (File file : files) {
				driveFiles.add(toDriveFile(file));
			}
		}
		return driveFiles;
	}

	private DriveFile toDriveFile(File file) {
		boolean isFolder = FOLDER_MIME_TYPE.equals(file.getMimeType());
		String modifiedTime = file.getModifiedTime() != null ? file.getModifiedTime().toString() : null;
		return new DriveFile(
				file.getId(),
				file.getName(),
				isFolder,
				file.getMimeType(),
				file.getSize(),
				modifiedTime);
	}

	private String escapeQueryString(String input) {
		return input.replace("\\", "\\\\").replace("'", "\\'");
	}

}
