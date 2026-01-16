package org.freeplane.plugin.googledrive;

import java.util.Map;
import java.util.WeakHashMap;

import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.googledrive.api.DriveFile;

/**
 * Tracks which maps were loaded from Google Drive, enabling "Save" to update
 * the same file without prompting for location.
 */
public class DriveMapTracker {

	private static final DriveMapTracker INSTANCE = new DriveMapTracker();

	private final Map<MapModel, DriveFile> mapToFile = new WeakHashMap<>();

	private DriveMapTracker() {
	}

	public static DriveMapTracker getInstance() {
		return INSTANCE;
	}

	public void registerMap(MapModel map, DriveFile driveFile) {
		mapToFile.put(map, driveFile);
	}

	public void unregisterMap(MapModel map) {
		mapToFile.remove(map);
	}

	public DriveFile getDriveFile(MapModel map) {
		return mapToFile.get(map);
	}

	public boolean isFromDrive(MapModel map) {
		return mapToFile.containsKey(map);
	}

	public void updateDriveFile(MapModel map, DriveFile driveFile) {
		if (mapToFile.containsKey(map)) {
			mapToFile.put(map, driveFile);
		}
	}

}
