/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.other_feature.OtherFeature;

@SearchTags({"last server"})
@DontBlock
public final class LastServerOtf extends OtherFeature
{
	public LastServerOtf()
	{
		super("最后服务器", "Wurst在服务器选择界面添加了一个\"最后服务器\"按钮，可以自动带您回到上一次游玩的服务器。\n\n"
			+ "当您被踢出服务器或服务器列表很长时非常有用。");
	}
}
