package com.pblabs.profiler;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class FlashIoUtils {
	public static CharsetDecoder stringDecoder = Charset.forName("UTF-8").newDecoder();

	public static String readFlashString(DataInputStream s) throws IOException
	{
		// Get the length.
		int len = s.readUnsignedShort();
		if(len == 0)
			return null;
			
		byte[] bytes = new byte[len];
		for(int i=0; i<len; i++)
			bytes[i] = s.readByte();
		
		return stringDecoder.decode(ByteBuffer.wrap(bytes)).toString();
	}
}
