package fiji.updater.ui;

import fiji.updater.logic.Dependency;
import fiji.updater.logic.PluginCollection;
import fiji.updater.logic.PluginCollection.DependencyMap;
import fiji.updater.logic.PluginObject;
import fiji.updater.logic.PluginObject.Action;

import fiji.updater.util.Util;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Toolkit;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextPane;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

public class ResolveDependencies extends JDialog implements ActionListener {
	UpdaterFrame updaterFrame;
	JPanel rootPanel;
	public JTextPane panel; // this is public for debugging purposes
	SimpleAttributeSet bold, indented, italic, normal, red;
	JButton ok, cancel;

	PluginCollection plugins;
	DependencyMap toInstall, obsoleted;
	Collection<PluginObject> automatic, ignore;
	int conflicts;
	boolean forUpload, wasCanceled;

	public ResolveDependencies(UpdaterFrame owner, PluginCollection plugins) {
		this(owner, plugins, false);
	}

	public ResolveDependencies(UpdaterFrame owner, PluginCollection plugins, boolean forUpload) {
		super(owner, "Resolve dependencies");

		updaterFrame = owner;
		this.forUpload = forUpload;
		this.plugins = plugins;

		rootPanel = SwingTools.verticalPanel();
		setContentPane(rootPanel);

		panel = new JTextPane();
		panel.setEditable(false);

		bold = new SimpleAttributeSet();
		StyleConstants.setBold(bold, true);
		StyleConstants.setFontSize(bold, 16);
		indented = new SimpleAttributeSet();
		StyleConstants.setLeftIndent(indented, 40);
		italic = new SimpleAttributeSet();
		StyleConstants.setItalic(italic, true);
		normal = new SimpleAttributeSet();
		red = new SimpleAttributeSet();
                StyleConstants.setForeground(red, Color.RED);

		SwingTools.scrollPane(panel, 450, 350, rootPanel);

		JPanel buttons = new JPanel();
		ok = SwingTools.button("OK", "OK", this, buttons);
		cancel = SwingTools.button("Cancel", "Cancel", this, buttons);
		rootPanel.add(buttons);

		// do not show, right now
		pack();
		setModal(true);
		setLocationRelativeTo(owner);

		int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
		SwingTools.addAccelerator(cancel, rootPanel, this,
				KeyEvent.VK_ESCAPE, 0);
		SwingTools.addAccelerator(cancel, rootPanel, this,
				KeyEvent.VK_W, ctrl);
		SwingTools.addAccelerator(ok, rootPanel, this,
				KeyEvent.VK_ENTER, 0);

		ignore = new HashSet<PluginObject>();
	}

	public void setVisible(boolean visible) {
		if (updaterFrame == null || !updaterFrame.hidden)
			super.setVisible(visible);
	}

	public void dispose() {
		if (updaterFrame != null && updaterFrame.hidden) synchronized (this) {
			notifyAll();
		}
		super.dispose();
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == cancel) {
			wasCanceled = true;
			dispose();
		}
		else if (e.getSource() == ok) {
			if (!ok.isEnabled())
				return;
			for (PluginObject plugin : automatic)
				plugin.setFirstValidAction(plugins, new Action[] {
					Action.INSTALL, Action.UPDATE,
					Action.UNINSTALL
				});
			dispose();
		}
	}

	void enableOKIfValid() {
		ok.setEnabled(conflicts == 0);
		if (ok.isEnabled())
			ok.requestFocus();
	}

	public boolean resolve() {
		listIssues();

		if (panel.getDocument().getLength() > 0)
			setVisible(true);
		return !wasCanceled;
	}

	void listIssues() {
		conflicts = 0;
		panel.setText("");

		automatic = new ArrayList<PluginObject>();
		if (forUpload)
			listUploadIssues();
		else
			listUpdateIssues();

		enableOKIfValid();
		if (isShowing()) {
			if (panel.getStyledDocument().getLength() == 0)
				addText("No more issues to be resolved!",
						italic);
			panel.setCaretPosition(0);
			panel.repaint();
		}
	}

	void listUpdateIssues() {
		toInstall = plugins.getDependencies(false);
		obsoleted = plugins.getDependencies(true);

		for (PluginObject plugin : toInstall.keySet())
			if (obsoleted.get(plugin) != null)
				bothInstallAndUninstall(plugin);
			else if (!plugin.willBeUpToDate()) {
				if (plugin.isLocallyModified())
					locallyModified(plugin);
				else
					automatic.add(plugin);
			}

		for (PluginObject plugin : obsoleted.keySet())
			if (toInstall.get(plugin) != null && // handled above
					!plugin.willNotBeInstalled())
				needUninstall(plugin);

		if (automatic.size() > 0) {
			maybeAddSeparator();
			newText("These components will be updated/"
					+ "installed automatically: \n\n");
			addList(automatic);
		}
	}

	void bothInstallAndUninstall(PluginObject plugin) {
		maybeAddSeparator();
		PluginCollection reasons =
			PluginCollection.clone(toInstall.get(plugin));
		reasons.addAll(obsoleted.get(plugin));
		newText("Conflict: ", red);
		addText(plugin.getFilename(), bold);
		addText(" is required by\n\n");
		addList(toInstall.get(plugin));
		addText("\nbut made obsolete by\n\n");
		addList(obsoleted.get(plugin));
		addText("\n    ");
		addIgnoreButton("Ignore this issue", plugin);
		addText("    ");
		addButton("Do not update " + reasons, reasons, null);
	}

	void needUninstall(PluginObject plugin) {
		maybeAddSeparator();
		PluginCollection reasons = obsoleted.get(plugin);
		newText("Conflict: ", red);
		addText(plugin.getFilename(), bold);
		addText(" is locally modified but made obsolete by\n\n");
		addList(reasons);
		addText("\n    ");
		addButton("Uninstall " + plugin, plugin, Action.UNINSTALL);
		addText("    ");
		addButton("Do not update " + reasons, reasons, null);
	}

	void locallyModified(PluginObject plugin) {
		if (ignore.contains(plugin))
			return;
		maybeAddSeparator();
		newText("Warning: ");
		addText(plugin.getFilename(), bold);
		addText(" is locally modified and Fiji cannot determine its "
			+ "status. A newer version might be required by\n\n");
		addList(toInstall.get(plugin));
		addText("\n    ");
		addIgnoreButton("Keep the local version", plugin);
		addText("    ");
		boolean toInstall = plugin.getStatus().isValid(Action.INSTALL);
		addButton((toInstall ? "Install" : "Update") + " " + plugin,
			plugin, toInstall ? Action.INSTALL : Action.UPDATE);
	}

	void listUploadIssues() {
		toInstall = new PluginCollection.DependencyMap();
		for (PluginObject plugin : plugins.toUpload())
			for (Dependency dependency : plugin.getDependencies()) {
				PluginObject dep =
					plugins.getPlugin(dependency.filename);
				if (dep == null || ignore.contains(dep))
					continue;
				if (dep.isInstallable() ||
					(!dep.isFiji() &&
					 dep.getAction() != Action.UPLOAD) ||
					dep.isObsolete() ||
					(dep.getStatus().isValid(Action.UPLOAD)
					 && dep.getAction() != Action.UPLOAD))
					toInstall.add(dep, plugin);
			}
		for (PluginObject plugin : toInstall.keySet())
			needUpload(plugin);

		// Replace dependencies on to-be-removed plugins
		for (PluginObject plugin : plugins.fijiPlugins()) {
			if (plugin.getAction() == Action.REMOVE)
				continue;
			for (Dependency dependency : plugin.getDependencies())
				if (plugins.getPlugin(dependency.filename) == null)
					dependencyNotUploaded(plugin, dependency.filename);
				else if (plugins.getPlugin(dependency.filename).getAction()
						== Action.REMOVE)
					dependencyRemoved(plugin, dependency.filename);
		}
	}

	void needUpload(final PluginObject plugin) {
		boolean notFiji = !plugin.isFiji();
		boolean notInstalled = plugin.isInstallable();
		boolean obsolete = plugin.isObsolete();
		final PluginCollection reasons = toInstall.get(plugin);
		maybeAddSeparator();
		newText("Warning: ", notFiji || obsolete ? red : normal);
		addText(plugin.getFilename(), bold);
		addText(" is " + (notFiji ? "not a Fiji component yet" :
			(notInstalled ? "not installed" :
			(obsolete ? "marked obsolete" : "locally modified")))
			+ " but a dependency of\n\n");
		addList(reasons);
		addText("\n    ");
		if (!notFiji && !obsolete) {
			addIgnoreButton("Do not upload " + plugin, plugin);
			addText("    ");
		}
		if (!notInstalled) {
			addButton("Upload " + plugin
					+ (obsolete ? " again" : ""),
					plugin, Action.UPLOAD);
			addText("    ");
		}
		addButton("Break the dependency", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for (PluginObject other : reasons)
					other.removeDependency(plugin
						.getFilename());
				listIssues();
			}
		});
	}

	void dependencyNotUploaded(final PluginObject plugin, final String dependency) {
		maybeAddSeparator();
		newText("Error: ", normal);
		addText(plugin.getFilename(), bold);
		addText(" depends on " + dependency + " which is not a Fiji plugin.\n\n");
		addDependencyButton("Break the dependency", plugin, dependency, null);
	}

	void dependencyRemoved(final PluginObject plugin,
			final String dependency) {
		maybeAddSeparator();
		newText("Warning: ", normal);
		addText(plugin.getFilename(), bold);
		addText(" depends on " + dependency + " which is about to be removed.\n\n");
		addDependencyButton("Break the dependency", plugin,
				dependency, null);
		for (PluginObject toUpload : plugins.toUpload()) {
			if (plugin.hasDependency(toUpload.getFilename()))
				continue;
			addText("    ");
			addDependencyButton("Replace with dependency to "
					+ toUpload, plugin, dependency,
					toUpload.getFilename());
			addText("    ");
			addDependencyButton("Replace all dependencies on "
					+ dependency + " with dependencies to "
					+ toUpload, null, dependency,
					toUpload.getFilename());
		}
	}

	void addIgnoreButton(String label, final PluginObject plugin) {
		addButton(label, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ignore.add(plugin);
				listIssues();
			}
		});
	}

	void addDependencyButton(String label, final PluginObject plugin,
			final String removeDependency,
			final String addDependency) {
		addButton(label, new ActionListener() {
			void replaceDependency(PluginObject plugin) {
				plugin.removeDependency(removeDependency);
				if (addDependency != null)
					plugin.addDependency(addDependency);
			}

			public void actionPerformed(ActionEvent e) {
				if (plugin != null)
					replaceDependency(plugin);
				else
					for (PluginObject plugin : plugins)
						if (plugin.hasDependency(removeDependency))
							replaceDependency(plugin);
				listIssues();
			}
		});
	}

	void addButton(String label, PluginObject plugin, Action action) {
		Collection<PluginObject> one = new ArrayList<PluginObject>();
		one.add(plugin);
		addButton(label, one, action);
	}

	void addButton(String label, final Collection<PluginObject> plugins,
			final Action action) {
		addButton(label, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for (PluginObject plugin : plugins)
					if (action == null)
						plugin.setNoAction();
					else
						plugin.setAction(ResolveDependencies.this.plugins, action);
				listIssues();
			}
		});
	}

	void addButton(String label, ActionListener listener) {
		conflicts++;
		JButton button = SwingTools.button(label, null, listener, null);
		selectEnd();
		panel.insertComponent(button);
	}

	void selectEnd() {
		int end = panel.getStyledDocument().getLength();
		panel.select(end, end);
	}

	void newText(String message) {
		newText(message, normal);
	}

	void newText(String message, SimpleAttributeSet style) {
		if (panel.getStyledDocument().getLength() > 0)
			addText("\n\n");
		addText(message, style);
	}

	void addText(String message) {
		addText(message, normal);
	}

	void addText(String message, SimpleAttributeSet style) {
		int end = panel.getStyledDocument().getLength();
		try {
			panel.getStyledDocument().insertString(end,
					message, style);
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	void addList(Collection<PluginObject> list) {
		addText(Util.join(", ", list) + "\n", bold);
		int end = panel.getStyledDocument().getLength();
		panel.select(end - 1, end - 1);
		panel.setParagraphAttributes(indented, true);
	}

	protected void maybeAddSeparator() {
		if (panel.getText().equals("") && panel.getComponents().length == 0)
			return;
		addText("\n");
		selectEnd();
		panel.insertComponent(new JSeparator());
	}

	private static PluginObject fakePlugin(String label) {
		Random random = new Random();
		String fakeChecksum = "";
		for (int i = 0; i < 20; i++)
			fakeChecksum += Integer.toHexString(random.nextInt() & 0xf) + Integer.toHexString(random.nextInt() & 0xf);
		long fakeTimestamp = 19700000000000l + (random.nextLong() % 400000000000l);
		return new PluginObject("", label, fakeChecksum, fakeTimestamp, PluginObject.Status.NOT_INSTALLED);
	}

	public static void main(String[] args) {
		PluginCollection plugins = new PluginCollection();
		ResolveDependencies frame = new ResolveDependencies(null, plugins);

		PluginObject plugin = fakePlugin("Install_.jar");
		plugin.addDependency("Obsoleted_.jar");
		plugin.addDependency("Locally_Modified.jar");
		plugin.setStatus(PluginObject.Status.NOT_INSTALLED);
		plugin.setAction(plugins, PluginObject.Action.INSTALL);
		plugins.add(plugin);
		plugin = fakePlugin("Obsoleting_.jar");
		plugin.addDependency("Obsoleted_.jar");
		plugin.setStatus(PluginObject.Status.NOT_INSTALLED);
		plugin.setAction(plugins, PluginObject.Action.INSTALL);
		plugins.add(plugin);
		plugin = fakePlugin("Locally_Modified.jar");
		plugin.setStatus(PluginObject.Status.MODIFIED);
		plugin.setAction(plugins, PluginObject.Action.MODIFIED);
		plugins.add(plugin);
		plugin = fakePlugin("Obsoleted_.jar");
		plugin.setStatus(PluginObject.Status.OBSOLETE);
		plugin.setAction(plugins, PluginObject.Action.OBSOLETE);
		plugins.add(plugin);

		System.err.println("resolved: " + frame.resolve());
	}
}
