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
	private static final String OPEN_TITLE = "Open from Google Drive";
	private static final String SAVE_TITLE = "Save to Google Drive";
	private static final Dimension DEFAULT_SIZE = new Dimension(600, 500);

	private final GoogleDriveClient driveClient;
	private final boolean folderSelectionMode;
	private final JTree fileTree;
	private final DriveTreeModel treeModel;
	private final JLabel breadcrumbLabel;
	private final JButton actionButton;
	private final JButton cancelButton;
	private final JButton refreshButton;
	private final JTextField searchField;
	private final JTextField fileNameField;
	private final JPanel fileNamePanel;

	private DriveFile selectedFile;
	private DriveFile selectedFolder;
	private boolean approved;

	public GoogleDriveFileBrowser(Frame owner, GoogleDriveClient driveClient) {
		this(owner, driveClient, false, null);
	}

	public GoogleDriveFileBrowser(Frame owner, GoogleDriveClient driveClient, boolean folderSelectionMode, String defaultFileName) {
		super(owner, folderSelectionMode ? SAVE_TITLE : OPEN_TITLE, true);
		this.driveClient = driveClient;
		this.folderSelectionMode = folderSelectionMode;

		treeModel = new DriveTreeModel();
		fileTree = new JTree(treeModel);
		fileTree.setCellRenderer(new DriveTreeCellRenderer());
		fileTree.setRootVisible(true);
		fileTree.setShowsRootHandles(true);

		breadcrumbLabel = new JLabel("Google Drive");
		actionButton = new JButton(folderSelectionMode ? "Save" : "Open");
		cancelButton = new JButton("Cancel");
		refreshButton = new JButton("Refresh");
		searchField = new JTextField(20);
		fileNameField = new JTextField(defaultFileName != null ? defaultFileName : "", 30);
		fileNamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		configureDialog();
		layoutComponents();
		attachListeners();

		if (folderSelectionMode) {
			selectedFolder = DriveFile.createRoot();
			updateSaveButtonState();
		}

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

		JPanel southPanel = new JPanel(new BorderLayout());

		if (folderSelectionMode) {
			fileNamePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
			fileNamePanel.add(new JLabel("File name:"));
			fileNamePanel.add(fileNameField);
			southPanel.add(fileNamePanel, BorderLayout.NORTH);
		}

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttonPanel.add(actionButton);
		buttonPanel.add(cancelButton);
		southPanel.add(buttonPanel, BorderLayout.SOUTH);

		add(southPanel, BorderLayout.SOUTH);

		actionButton.setEnabled(false);
	}

	private void attachListeners() {
		fileTree.addTreeSelectionListener(e -> {
			TreePath path = e.getPath();
			if (path != null) {
				Object node = path.getLastPathComponent();
				if (node instanceof DriveFileNode) {
					DriveFile file = ((DriveFileNode) node).getDriveFile();
					selectedFile = file;
					if (folderSelectionMode) {
						if (file.isFolder()) {
							selectedFolder = file;
							actionButton.setEnabled(true);
						} else {
							actionButton.setEnabled(selectedFolder != null);
						}
					} else {
						actionButton.setEnabled(!file.isFolder() && file.isMindMap());
					}
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
					if (folderSelectionMode) {
						// In folder selection mode, double-click on folder expands it (handled by tree)
						// Double-click on file does nothing - user must click Save button
					} else {
						if (!selectedFile.isFolder() && selectedFile.isMindMap()) {
							approveSelection();
						}
					}
				}
			}
		});

		actionButton.addActionListener(e -> approveSelection());
		cancelButton.addActionListener(e -> dispose());
		refreshButton.addActionListener(e -> refreshCurrentFolder());

		searchField.addActionListener(e -> performSearch());

		if (folderSelectionMode) {
			fileNameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
				@Override
				public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSaveButtonState(); }
				@Override
				public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSaveButtonState(); }
				@Override
				public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSaveButtonState(); }
			});
		}
	}

	private void updateSaveButtonState() {
		if (folderSelectionMode) {
			String fileName = fileNameField.getText().trim();
			actionButton.setEnabled(selectedFolder != null && !fileName.isEmpty());
		}
	}

	private void loadRootFolder() {
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			private List<DriveFile> myDriveFiles;
			private List<DriveFile> sharedFiles;

			@Override
			protected Void doInBackground() throws Exception {
				myDriveFiles = driveClient.listFiles("root");
				sharedFiles = driveClient.listSharedFiles();
				return null;
			}

			@Override
			protected void done() {
				setCursor(java.awt.Cursor.getDefaultCursor());
				try {
					get();
					treeModel.setRootFiles(myDriveFiles);
					treeModel.setSharedFiles(sharedFiles);
					fileTree.expandRow(0);
				} catch (Exception e) {
					LogUtils.warn("Failed to load Google Drive folders", e);
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
		breadcrumbLabel.setText("Google Drive");
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
		if (folderSelectionMode) {
			String fileName = fileNameField.getText().trim();
			if (selectedFolder != null && !fileName.isEmpty()) {
				approved = true;
				dispose();
			}
		} else {
			if (selectedFile != null && !selectedFile.isFolder() && selectedFile.isMindMap()) {
				approved = true;
				dispose();
			}
		}
	}

	public boolean showDialog() {
		setVisible(true);
		return approved;
	}

	public DriveFile getSelectedFile() {
		return selectedFile;
	}

	public DriveFile getSelectedFolder() {
		return selectedFolder;
	}

	public String getFileName() {
		String name = fileNameField.getText().trim();
		if (!name.toLowerCase().endsWith(".mm")) {
			name = name + ".mm";
		}
		return name;
	}

}
