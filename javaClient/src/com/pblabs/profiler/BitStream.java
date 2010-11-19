package com.pblabs.profiler;

import java.io.EOFException;

public class BitStream 
{
	private final int BYTE_LENGTH = 8;

	private byte bits[];
	private int currentBit;
	private int totalBits;
	private NetStringCache stringCache;
	
	public BitStream(int lengthInBytes)
	{
		bits = new byte[lengthInBytes];
		currentBit = 0;
		totalBits = lengthInBytes * BYTE_LENGTH;
	}
	
	public BitStream(int lengthInBytes, byte data[])
	{
		bits = data;
		currentBit = 0;
		totalBits = lengthInBytes * BYTE_LENGTH; 
	}
	
	public boolean readFlag() throws EOFException
	{
		if(currentBit >= totalBits)
			throw new EOFException();
		
		byte b = bits[currentBit>>3];
		return ((b >> (currentBit++ & 0x7)) & 0x1) == 1;
	}
	
	public byte readByte() throws EOFException
	{
		byte b = 0;
		for(int i=0; i<BYTE_LENGTH; i++)
			b |= boolToInt(readFlag()) << i;

		return b;
	}
	
	public int readInt(int bitCount) throws EOFException
	{
		int val = 0;
		for(int i=0; i<bitCount; i++)
			val |= boolToInt(readFlag()) << i;

		return val;
	}
	
	public float readFloat(int bitCount) throws EOFException
	{
		return readInt(bitCount) / (float)((1 << bitCount) - 1);
	}
	
	public boolean writeFlag(boolean value) throws EOFException
	{
		if(currentBit >= totalBits)
			throw new EOFException();
		
		if(value)
			bits[currentBit>>3] |= (1 << (currentBit & 0x7));
		else
			bits[currentBit>>3] &= ~(1 << (currentBit & 0x7));
		
		currentBit++;
		
		return value;
	}
	
	public void writeByte(byte value) throws EOFException
	{
		for(int i=0; i<BYTE_LENGTH; i++)
			writeFlag( ((value>>i)&1) == 1 );
	}
	
	public void writeInt(int value, int bitCount) throws EOFException
	{
		for(int i=0; i<bitCount; i++)
			writeFlag( ((value>>i)&1) == 1 );
	}
	
	public void writeFloat(float value, int bitCount) throws EOFException
	{
		writeInt((int)(value * ((1 << bitCount) - 1)), bitCount);
	}
	
	public void writeString(String s) throws EOFException
	{
		writeInt(s.length(), 10);
		for(int i=0; i<s.length(); i++)
			writeByte(s.getBytes()[i]);
	}
	
	public String readString() throws EOFException
	{
		int length = readInt(10);
		String result = new String();
		for(int i=0; i<length; i++)
		{
			result += (char)readByte();
		}
		
		return result;
	}
	
	/**
	 * Write int where min <= int < max.
	 * @throws EOFException 
	 */
	public void writeRangedInt(int value, int min, int max) throws EOFException
	{
		int range = max - min + 1;
		
		// TODO: Make this use bits and be fast.
		int bitCount = (int)Math.ceil(Math.log10(range) / Math.log10(2.0));
		
		writeInt(value - min, bitCount);
	}
	
	public int readRangedInt(int min, int max) throws EOFException
	{
		int range = max - min + 1;
		
		// TODO: Make this use bits and be fast.
		int bitCount = (int)Math.ceil(Math.log10(range) / Math.log10(2.0));
		
		return readInt(bitCount) + min;
	}
	
	public void setStringCache(NetStringCache nsc)
	{
		stringCache = nsc;
	}
	
	public NetStringCache getStringCache()
	{
		return stringCache;
	}
	
	public void writeCachedString(String s) throws EOFException
	{
		stringCache.write(this, s);
	}
	
	public String readCachedString() throws EOFException
	{
		return stringCache.read(this);
	}
	
	public int getLengthInBytes()
	{
		return (currentBit+7) >> 3;
	}
	
	public byte[] getBytes()
	{
		return bits;
	}
	
	public void reset()
	{
		currentBit = 0;
	}
	
	public boolean isEof()
	{
		return currentBit >= totalBits;
	}
	
	public int getCurrentPosition()
	{
		return currentBit;
	}
	
	public void setCurrentPosition(int bit) throws Exception
	{
		if(bit >= totalBits || bit < 0)
			throw new Exception("Out of bounds.");
		
		currentBit = bit;
	}
	
	public int getRemainingBits()
	{
		return (totalBits - 1) - currentBit;
	}
	
	private static int boolToInt(boolean bit) 
	{
		if(bit) return 1;
		else	return 0;
	}
}
