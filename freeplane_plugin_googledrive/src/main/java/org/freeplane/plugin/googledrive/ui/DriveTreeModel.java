package org.freeplane.plugin.googledrive.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.freeplane.plugin.googledrive.api.DriveFile;

class DriveTreeModel implements TreeModel {

	private final DriveFileNode root;
	private final DriveFileNode myDriveNode;
	private final DriveFileNode sharedWithMeNode;
	private final List<TreeModelListener> listeners;

	DriveTreeModel() {
		DriveFile topLevel = DriveFile.createTopLevel();
		this.root = new DriveFileNode(topLevel, null);

		DriveFile myDrive = DriveFile.createRoot();
		this.myDriveNode = new DriveFileNode(myDrive, root);
		root.addChildNode(myDriveNode);

		DriveFile sharedWithMe = DriveFile.createSharedWithMe();
		this.sharedWithMeNode = new DriveFileNode(sharedWithMe, root);
		root.addChildNode(sharedWithMeNode);

		this.listeners = new ArrayList<>();
	}

	DriveFileNode getMyDriveNode() {
		return myDriveNode;
	}

	DriveFileNode getSharedWithMeNode() {
		return sharedWithMeNode;
	}

	void setRootFiles(List<DriveFile> files) {
		myDriveNode.setChildren(files);
		fireTreeStructureChanged();
	}

	void setSharedFiles(List<DriveFile> files) {
		sharedWithMeNode.setChildren(files);
		fireTreeNodesChanged(sharedWithMeNode);
	}

	void setChildren(DriveFileNode parentNode, List<DriveFile> files) {
		parentNode.setChildren(files);
		fireTreeNodesChanged(parentNode);
	}

	void clearAndReload() {
		myDriveNode.clearChildren();
		sharedWithMeNode.clearChildren();
		fireTreeStructureChanged();
	}

	@Override
	public Object getRoot() {
		return root;
	}

	@Override
	public Object getChild(Object parent, int index) {
		DriveFileNode node = (DriveFileNode) parent;
		return node.getChildAt(index);
	}

	@Override
	public int getChildCount(Object parent) {
		DriveFileNode node = (DriveFileNode) parent;
		return node.getChildCount();
	}

	@Override
	public boolean isLeaf(Object node) {
		DriveFileNode fileNode = (DriveFileNode) node;
		return fileNode.isLeaf();
	}

	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		if (parent == null || child == null) {
			return -1;
		}
		DriveFileNode parentNode = (DriveFileNode) parent;
		return parentNode.getIndex((DriveFileNode) child);
	}

	@Override
	public void addTreeModelListener(TreeModelListener l) {
		listeners.add(l);
	}

	@Override
	public void removeTreeModelListener(TreeModelListener l) {
		listeners.remove(l);
	}

	private void fireTreeStructureChanged() {
		TreeModelEvent event = new TreeModelEvent(this, new Object[] { root });
		for (TreeModelListener listener : listeners) {
			listener.treeStructureChanged(event);
		}
	}

	private void fireTreeNodesChanged(DriveFileNode node) {
		TreePath path = getPathToRoot(node);
		TreeModelEvent event = new TreeModelEvent(this, path);
		for (TreeModelListener listener : listeners) {
			listener.treeStructureChanged(event);
		}
	}

	private TreePath getPathToRoot(DriveFileNode node) {
		List<DriveFileNode> path = new ArrayList<>();
		DriveFileNode current = node;
		while (current != null) {
			path.add(0, current);
			current = (DriveFileNode) current.getParent();
		}
		return new TreePath(path.toArray());
	}

}
