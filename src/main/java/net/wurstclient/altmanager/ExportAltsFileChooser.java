/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager;

import java.awt.Component;
import java.awt.HeadlessException;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.wurstclient.util.SwingUtils;

public final class ExportAltsFileChooser extends JFileChooser
{
	public static void main(String[] args)
	{
		SwingUtils.setLookAndFeel();
		
		int response = JOptionPane.showConfirmDialog(null, args[0], args[1],
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		
		if(response != JOptionPane.OK_OPTION)
			return;
		
		JFileChooser fileChooser = new ExportAltsFileChooser();
		
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setAcceptAllFileFilterUsed(false);
		
		FileNameExtensionFilter txtFilter =
			new FileNameExtensionFilter(args[2], "txt");
		fileChooser.addChoosableFileFilter(txtFilter);
		
		FileNameExtensionFilter jsonFilter =
			new FileNameExtensionFilter(args[3], "json");
		fileChooser.addChoosableFileFilter(jsonFilter);
		
		if(fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
			return;
		
		String path = fileChooser.getSelectedFile().getAbsolutePath();
		FileFilter fileFilter = fileChooser.getFileFilter();
		
		if(fileFilter == txtFilter && !path.endsWith(".txt"))
			path += ".txt";
		else if(fileFilter == jsonFilter && !path.endsWith(".json"))
			path += ".json";
		
		System.out.println(path);
	}
	
	@Override
	protected JDialog createDialog(Component parent) throws HeadlessException
	{
		JDialog dialog = super.createDialog(parent);
		dialog.setAlwaysOnTop(true);
		return dialog;
	}
	
}
