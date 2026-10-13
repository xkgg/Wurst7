/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.chattranslator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class GoogleTranslateTest
{
	@Test
	void failedRequestReturnsNull() throws IOException
	{
		assertNull(GoogleTranslate.translate("hello", response(null)));
	}
	
	@Test
	void missingTranslationReturnsNull() throws IOException
	{
		assertNull(GoogleTranslate.translate("hello", response("")));
		assertNull(GoogleTranslate.translate("hello",
			response("<html>Service unavailable</html>")));
		assertNull(GoogleTranslate.translate("hello",
			response("<div class=\"result-container\"></div>")));
	}
	
	@Test
	void successfulTranslationDecodesEntities() throws IOException
	{
		assertEquals("Hallo & willkommen",
			GoogleTranslate.translate("hello", response(
				"<div class=\"result-container\">Hallo &amp; willkommen</div>")));
	}
	
	@Test
	void unchangedTextReturnsNull() throws IOException
	{
		assertNull(GoogleTranslate.translate(" Hello ",
			response("<div class=\"result-container\">HELLO</div>")));
	}
	
	private static URL response(String html) throws IOException
	{
		return new URL(null, "https://translate.google.com/m",
			new URLStreamHandler()
			{
				@Override
				protected URLConnection openConnection(URL url)
				{
					return new URLConnection(url)
					{
						@Override
						public void connect()
						{}
						
						@Override
						public InputStream getInputStream() throws IOException
						{
							assertEquals(5000, getConnectTimeout());
							assertEquals(5000, getReadTimeout());
							if(html == null)
								throw new IOException(
									"Simulated translation request failure");
							
							return new ByteArrayInputStream(
								html.getBytes(StandardCharsets.UTF_8));
						}
					};
				}
			});
	}
}
