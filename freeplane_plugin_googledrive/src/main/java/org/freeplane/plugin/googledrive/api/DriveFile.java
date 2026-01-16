package org.freeplane.plugin.googledrive.api;

public class DriveFile {

	private final String id;
	private final String name;
	private final boolean folder;
	private final String mimeType;
	private final Long size;
	private final String modifiedTime;

	public DriveFile(String id, String name, boolean folder, String mimeType) {
		this(id, name, folder, mimeType, null, null);
	}

	public DriveFile(String id, String name, boolean folder, String mimeType, Long size, String modifiedTime) {
		this.id = id;
		this.name = name;
		this.folder = folder;
		this.mimeType = mimeType;
		this.size = size;
		this.modifiedTime = modifiedTime;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isFolder() {
		return folder;
	}

	public String getMimeType() {
		return mimeType;
	}

	public Long getSize() {
		return size;
	}

	public String getModifiedTime() {
		return modifiedTime;
	}

	public boolean isMindMap() {
		return name != null && name.toLowerCase().endsWith(".mm");
	}

	@Override
	public String toString() {
		return name;
	}

}
