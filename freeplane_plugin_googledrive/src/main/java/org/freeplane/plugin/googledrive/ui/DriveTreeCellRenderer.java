package org.freeplane.plugin.googledrive.ui;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.freeplane.plugin.googledrive.api.DriveFile;

class DriveTreeCellRenderer extends DefaultTreeCellRenderer {

	private static final long serialVersionUID = 1L;

	private final Icon folderIcon;
	private final Icon fileIcon;
	private final Icon mindMapIcon;

	DriveTreeCellRenderer() {
		folderIcon = UIManager.getIcon("FileView.directoryIcon");
		fileIcon = UIManager.getIcon("FileView.fileIcon");
		mindMapIcon = UIManager.getIcon("FileView.fileIcon");
	}

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
			boolean leaf, int row, boolean hasFocus) {

		super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

		if (value instanceof DriveFileNode) {
			DriveFile file = ((DriveFileNode) value).getDriveFile();
			setText(file.getName());

			if (file.isFolder()) {
				setIcon(folderIcon);
			} else if (file.isMindMap()) {
				setIcon(mindMapIcon);
			} else {
				setIcon(fileIcon);
			}
		}

		return this;
	}

}
