package org.freeplane.plugin.googledrive.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.googledrive.api.DriveFile;
import org.freeplane.plugin.googledrive.api.GoogleDriveClient;

public class GoogleDriveFileBrowser extends JDialog {

	private static final long serialVersionUID = 1L;
	private static final String TITLE = "Open from Google Drive";
	private static final Dimension DEFAULT_SIZE = new Dimension(600, 500);

	private final GoogleDriveClient driveClient;
	private final JTree fileTree;
	private final DriveTreeModel treeModel;
	private final JLabel breadcrumbLabel;
	private final JButton openButton;
	private final JButton cancelButton;
	private final JButton refreshButton;
	private final JTextField searchField;

	private DriveFile selectedFile;
	private boolean approved;

	public GoogleDriveFileBrowser(Frame owner, GoogleDriveClient driveClient) {
		super(owner, TITLE, true);
		this.driveClient = driveClient;

		treeModel = new DriveTreeModel();
		fileTree = new JTree(treeModel);
		fileTree.setCellRenderer(new DriveTreeCellRenderer());
		fileTree.setRootVisible(true);
		fileTree.setShowsRootHandles(true);

		breadcrumbLabel = new JLabel("My Drive");
		openButton = new JButton("Open");
		cancelButton = new JButton("Cancel");
		refreshButton = new JButton("Refresh");
		searchField = new JTextField(20);

		configureDialog();
		layoutComponents();
		attachListeners();

		loadRootFolder();
	}

	private void configureDialog() {
		setSize(DEFAULT_SIZE);
		setLocationRelativeTo(getOwner());
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	}

	private void layoutComponents() {
		setLayout(new BorderLayout(5, 5));

		JPanel topPanel = new JPanel(new BorderLayout(5, 5));
		topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		topPanel.add(breadcrumbLabel, BorderLayout.WEST);

		JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		searchPanel.add(new JLabel("Search:"));
		searchPanel.add(searchField);
		searchPanel.add(refreshButton);
		topPanel.add(searchPanel, BorderLayout.EAST);

		add(topPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(fileTree);
		add(scrollPane, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttonPanel.add(openButton);
		buttonPanel.add(cancelButton);
		add(buttonPanel, BorderLayout.SOUTH);

		openButton.setEnabled(false);
	}

	private void attachListeners() {
		fileTree.addTreeSelectionListener(e -> {
			TreePath path = e.getPath();
			if (path != null) {
				Object node = path.getLastPathComponent();
				if (node instanceof DriveFileNode) {
					DriveFile file = ((DriveFileNode) node).getDriveFile();
					selectedFile = file;
					openButton.setEnabled(!file.isFolder() && file.isMindMap());
					updateBreadcrumb(path);
				}
			}
		});

		fileTree.addTreeWillExpandListener(new TreeWillExpandListener() {
			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
				TreePath path = event.getPath();
				Object node = path.getLastPathComponent();
				if (node instanceof DriveFileNode) {
					DriveFileNode fileNode = (DriveFileNode) node;
					if (!fileNode.areChildrenLoaded() && fileNode.getDriveFile().isFolder()) {
						loadFolder(fileNode);
					}
				}
			}

			@Override
			public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
			}
		});

		fileTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && selectedFile != null) {
					if (!selectedFile.isFolder() && selectedFile.isMindMap()) {
						approveSelection();
					}
				}
			}
		});

		openButton.addActionListener(e -> approveSelection());
		cancelButton.addActionListener(e -> dispose());
		refreshButton.addActionListener(e -> refreshCurrentFolder());

		searchField.addActionListener(e -> performSearch());
	}

	private void loadRootFolder() {
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

		SwingWorker<List<DriveFile>, Void> worker = new SwingWorker<List<DriveFile>, Void>() {
			@Override
			protected List<DriveFile> doInBackground() throws Exception {
				return driveClient.listFiles("root");
			}

			@Override
			protected void done() {
				setCursor(java.awt.Cursor.getDefaultCursor());
				try {
					List<DriveFile> files = get();
					treeModel.setRootFiles(files);
					fileTree.expandRow(0);
				} catch (Exception e) {
					LogUtils.warn("Failed to load Google Drive root folder", e);
					UITools.errorMessage("Failed to load Drive files: " + e.getMessage());
				}
			}
		};
		worker.execute();
	}

	private void loadFolder(DriveFileNode folderNode) {
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

		SwingWorker<List<DriveFile>, Void> worker = new SwingWorker<List<DriveFile>, Void>() {
			@Override
			protected List<DriveFile> doInBackground() throws Exception {
				return driveClient.listFiles(folderNode.getDriveFile().getId());
			}

			@Override
			protected void done() {
				setCursor(java.awt.Cursor.getDefaultCursor());
				try {
					List<DriveFile> files = get();
					treeModel.setChildren(folderNode, files);
				} catch (Exception e) {
					LogUtils.warn("Failed to load folder contents", e);
					UITools.errorMessage("Failed to load folder: " + e.getMessage());
				}
			}
		};
		worker.execute();
	}

	private void refreshCurrentFolder() {
		treeModel.clearAndReload();
		loadRootFolder();
	}

	private void performSearch() {
		String searchTerm = searchField.getText().trim();
		if (searchTerm.isEmpty()) {
			refreshCurrentFolder();
			return;
		}

		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

		SwingWorker<List<DriveFile>, Void> worker = new SwingWorker<List<DriveFile>, Void>() {
			@Override
			protected List<DriveFile> doInBackground() throws IOException {
				return driveClient.searchMindMaps(searchTerm);
			}

			@Override
			protected void done() {
				setCursor(java.awt.Cursor.getDefaultCursor());
				try {
					List<DriveFile> files = get();
					treeModel.setRootFiles(files);
					breadcrumbLabel.setText("Search results: " + searchTerm);
				} catch (Exception e) {
					LogUtils.warn("Failed to search Google Drive", e);
					UITools.errorMessage("Search failed: " + e.getMessage());
				}
			}
		};
		worker.execute();
	}

	private void updateBreadcrumb(TreePath path) {
		StringBuilder sb = new StringBuilder();
		Object[] pathComponents = path.getPath();
		for (int i = 0; i < pathComponents.length; i++) {
			if (i > 0) {
				sb.append(" > ");
			}
			sb.append(pathComponents[i].toString());
		}
		breadcrumbLabel.setText(sb.toString());
	}

	private void approveSelection() {
		if (selectedFile != null && !selectedFile.isFolder() && selectedFile.isMindMap()) {
			approved = true;
			dispose();
		}
	}

	public boolean showDialog() {
		setVisible(true);
		return approved;
	}

	public DriveFile getSelectedFile() {
		return selectedFile;
	}

}
