package org.freeplane.plugin.googledrive.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.MapWriter;
import org.freeplane.features.map.clipboard.MapClipboardController.CopiedNodeSet;
import org.freeplane.features.mode.Controller;

public class MapSerializer {

	public static byte[] serializeMap(MapModel map) throws IOException {
		StringWriter writer = new StringWriter();
		Controller.getCurrentModeController()
				.getMapController()
				.getMapWriter()
				.writeMapAsXml(map, writer, MapWriter.Mode.FILE, CopiedNodeSet.ALL_NODES, false);
		return writer.toString().getBytes(StandardCharsets.UTF_8);
	}

	public static InputStream serializeMapAsStream(MapModel map) throws IOException {
		return new ByteArrayInputStream(serializeMap(map));
	}

}
