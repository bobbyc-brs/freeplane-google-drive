package org.freeplane.plugin.googledrive;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.googledrive.api.DriveFile;

public class DriveMapTracker {

	private static final DriveMapTracker INSTANCE = new DriveMapTracker();

	private final Map<MapModel, DriveFile> mapToFile = new WeakHashMap<>();
	private final Map<MapModel, String> lastKnownRemoteModifiedTime = new WeakHashMap<>();

	private DriveMapTracker() {
	}

	public static DriveMapTracker getInstance() {
		return INSTANCE;
	}

	public void registerMap(MapModel map, DriveFile driveFile) {
		mapToFile.put(map, driveFile);
		if (driveFile.getModifiedTime() != null) {
			lastKnownRemoteModifiedTime.put(map, driveFile.getModifiedTime());
		}
	}

	public void unregisterMap(MapModel map) {
		mapToFile.remove(map);
		lastKnownRemoteModifiedTime.remove(map);
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
			if (driveFile.getModifiedTime() != null) {
				lastKnownRemoteModifiedTime.put(map, driveFile.getModifiedTime());
			}
		}
	}

	public String getLastKnownModifiedTime(MapModel map) {
		return lastKnownRemoteModifiedTime.get(map);
	}

	public void updateLastKnownModifiedTime(MapModel map, String time) {
		if (mapToFile.containsKey(map) && time != null) {
			lastKnownRemoteModifiedTime.put(map, time);
		}
	}

	public Set<MapModel> getTrackedMaps() {
		return new HashSet<>(mapToFile.keySet());
	}

}
