package org.freeplane.plugin.googledrive.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.swing.tree.TreeNode;

import org.freeplane.plugin.googledrive.api.DriveFile;

class DriveFileNode implements TreeNode {

	private final DriveFile driveFile;
	private final DriveFileNode parent;
	private List<DriveFileNode> children;
	private boolean childrenLoaded;

	DriveFileNode(DriveFile driveFile, DriveFileNode parent) {
		this.driveFile = driveFile;
		this.parent = parent;
		this.children = new ArrayList<>();
		this.childrenLoaded = false;
	}

	DriveFile getDriveFile() {
		return driveFile;
	}

	void setChildren(List<DriveFile> files) {
		children.clear();
		for (DriveFile file : files) {
			children.add(new DriveFileNode(file, this));
		}
		childrenLoaded = true;
	}

	void addChildNode(DriveFileNode child) {
		children.add(child);
		childrenLoaded = true;
	}

	boolean areChildrenLoaded() {
		return childrenLoaded;
	}

	void clearChildren() {
		children.clear();
		childrenLoaded = false;
	}

	@Override
	public TreeNode getChildAt(int childIndex) {
		return children.get(childIndex);
	}

	@Override
	public int getChildCount() {
		return children.size();
	}

	@Override
	public TreeNode getParent() {
		return parent;
	}

	@Override
	public int getIndex(TreeNode node) {
		return children.indexOf(node);
	}

	@Override
	public boolean getAllowsChildren() {
		return driveFile.isFolder();
	}

	@Override
	public boolean isLeaf() {
		if (!driveFile.isFolder()) {
			return true;
		}
		if (!childrenLoaded) {
			return false;
		}
		return children.isEmpty();
	}

	@Override
	public Enumeration<DriveFileNode> children() {
		return Collections.enumeration(children);
	}

	@Override
	public String toString() {
		return driveFile.getName();
	}

}
