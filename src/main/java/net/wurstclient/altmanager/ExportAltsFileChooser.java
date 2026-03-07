/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
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
		
		int response = JOptionPane.showConfirmDialog(null,
			"这将创建一个未加密（纯文本）的alt列表副本。\n"
				+ "以纯文本形式存储密码是有风险的，因为它们很容易被病毒窃取。\n"
				+ "将此副本存储在安全的地方，并将其保存在Minecraft文件夹之外！",
			"警告", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE);
		
		if(response != JOptionPane.OK_OPTION)
			return;
		
		JFileChooser fileChooser = new ExportAltsFileChooser();
		
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setAcceptAllFileFilterUsed(false);
		
		FileNameExtensionFilter txtFilter =
			new FileNameExtensionFilter("TXT文件 (用户名:密码)", "txt");
		fileChooser.addChoosableFileFilter(txtFilter);
		
		FileNameExtensionFilter jsonFilter =
			new FileNameExtensionFilter("JSON文件", "json");
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
