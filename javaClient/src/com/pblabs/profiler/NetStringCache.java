package com.pblabs.profiler;

import java.io.EOFException;
import java.util.HashMap;

import org.slf4j.LoggerFactory;


public class NetStringCache {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NetStringCache.class);

	/**
	 * Number of bits to use for encoding string references.
	 */
	private int mStringRefBitCount = 10;
	private int mEntryCount;
	private CacheEntry mIDLookupTable[];
	private HashMap<String, CacheEntry> mStringHashLookupTable;
	private CacheEntry mCacheEntries[], mLRUHead, mLRUTail;
	
	private int mWriteCount, mCachedWriteCount;
	private int mReadCount, mCachedReadCount;
	private float mBytesSubmitted, mBytesWritten;
	private float mBytesEmitted, mBytesRead;
	
	public NetStringCache()
	{
		mEntryCount = 1<<mStringRefBitCount;
		
		mIDLookupTable         = new CacheEntry[mEntryCount];
		mStringHashLookupTable = new HashMap<String, CacheEntry>();
		mCacheEntries          = new CacheEntry[mEntryCount];

		// Sentinels for start/end of list.
		mLRUHead = new CacheEntry();
		mLRUTail = new CacheEntry();

		// Fill in all our cache entries.
		for(int i=0; i<mEntryCount; i++)
		{
			CacheEntry curEntry = mCacheEntries[i] = new CacheEntry();
			
			// Get the preceding entry.
			CacheEntry prevEntry;
			if(i==0)
				prevEntry = mLRUHead;
			else
				prevEntry = mCacheEntries[i-1];
			
			// Now set up links.
			curEntry.mLRUPrev = prevEntry;
			curEntry.mLRUNext = mLRUTail;
			prevEntry.mLRUNext = curEntry;
			
			// Fill in the ID.
			curEntry.mID = i;
			mIDLookupTable[i] = curEntry;
		}
		
		// Set up the end sentinel.
		mLRUTail.mLRUPrev = mCacheEntries[mEntryCount-1];		
	}
	
	/**
	 * Read a string from a BitStream, updating internal cache
	 * state as appropriate.
	 * @param bs
	 * @return
	 */
	public String read(BitStream bs) throws EOFException
	{
		boolean newString = false;
		int stringId = 0;
		
		mReadCount++;
		
		String readString = null;
		
		// First, check if this is an update or a cached entry.
		if(bs.readFlag())
		{
			newString = true;
			// This is some new data...
			
			int startRead = bs.getCurrentPosition();
			
			// Read the ID we're overwriting.
			int newId = bs.readInt(mStringRefBitCount);
			
			//logger.info("new id:"+newId);
			
			// And the string.
			readString = bs.readString();

			//logger.info("read new string:"+readString);

			mBytesRead += (float)(bs.getCurrentPosition() / startRead) / 8.0;
			
			// Overwrite the cache.
			reuseEntry(lookupById(newId), readString);
		}
		else
		{
			// This is referring to cached data...
			int oldId = bs.readInt(mStringRefBitCount);

			//logger.info("old id:"+oldId);

			// Look it up in our cache.
			readString = lookupById(oldId).mValue;
			
			mBytesRead += (float)mStringRefBitCount / 8.0;
			
			mCachedReadCount++;
		}
		
		if (readString!=null) {
			mBytesEmitted += readString.getBytes().length;
		} else {
			/*
			logger.info("Null string");
			if (newString)
				logger.info("String was new");
			else {
				logger.info("String was supposed to be found, id="+stringId);
			}
			*/
		}
		return readString;
	}
	
	public void write(BitStream bs, String s) throws EOFException
	{
		mWriteCount++;
		mBytesSubmitted += s.getBytes().length;
		
		// Find the string in our hash.
		CacheEntry ce = lookupByString(s);
		Boolean found = true;
		
		if(ce == null)
		{
			// Don't know about this string, so set it up (and note we did so).
			found = false;		
			ce = getOldestEntry();
			reuseEntry(ce, s);
		}	
		
		// Note the ID.
		int foundId = ce.mID;			
		
		// Is it new?
		if(bs.writeFlag(!found))
		{
			// Write the ID, and the string.
			bs.writeInt(foundId, mStringRefBitCount);			
			bs.writeString(s);
			
			mBytesWritten += (mStringRefBitCount / 8.0) + s.getBytes().length;
		}
		else
		{
			// Great, write the ID and we're done.
			bs.writeInt(foundId, mStringRefBitCount);
			
			mCachedWriteCount++;
			mBytesWritten += (mStringRefBitCount / 8.0);
		}
	}

	/**
	 * Take passed CacheEntry and bring it to the front of our cache.
	 * @param ce
	 */
	private void bringToFront(CacheEntry ce)
	{
		// Unlink it from where it is the list...
		ce.mLRUPrev.mLRUNext = ce.mLRUNext;
		ce.mLRUNext.mLRUPrev = ce.mLRUPrev;
		
		// And link it at the head.
		ce.mLRUNext = mLRUHead.mLRUNext;
		ce.mLRUNext.mLRUPrev = ce;
		ce.mLRUPrev = mLRUHead;
		mLRUHead.mLRUNext = ce;
	}
	
	/**
	 * Get the oldest entry, probably so you can overwrite it.
	 */
	private CacheEntry getOldestEntry()
	{
		return mLRUTail.mLRUPrev;
	}
	
	/**
	 * Remove an entry from secondary data structures, assign a new string
	 * to it, and reinsert it. 
	 */
	private void reuseEntry(CacheEntry ce, String newString)
	{
		// Get it out of the string map.
		if(ce.mValue != null)
			mStringHashLookupTable.remove(ce.mValue);
		
		// Assign the new string.
		ce.mValue = newString;
		
		// Reinsert string map.
		mStringHashLookupTable.put(newString, ce);
		
		// Bring to front of cache.
		bringToFront(ce);
	}
	
	/**
	 * Look up entry by string.
	 */
	private CacheEntry lookupByString(String s)
	{
		return mStringHashLookupTable.get(s);
	}
	
	/**
	 * Look up entry by its id.
	 */
	private CacheEntry lookupById(int id)
	{
		return mIDLookupTable[id];
	}
	
	public void reportStatistics()
	{
		logger.info("Report for NetStringCache " + this.toString());
		logger.info("   - " + mReadCount + " reads, " + mCachedReadCount + " cached (" 
				+ ((float)mCachedReadCount / (float)mReadCount)*100.0 + "% cached.)");
		logger.info("   - " + mWriteCount + " writes, " + mCachedWriteCount + " cached (" 
				+ ((float)mCachedWriteCount / (float)mWriteCount)*100.0 + "% cached.)");
		
		// How much of the cache is used?
		int numUsed = 0;
		for(int i=0; i<mEntryCount; i++)
		{
			if(mIDLookupTable[i] == null || mIDLookupTable[i].mValue == null)
				continue;
			
			numUsed++;
		}
		
		logger.info("   - " + numUsed + " out of " + mEntryCount + " IDs in use (" 
				+ ((float)numUsed / (float)mEntryCount)*100.0 + "% utilization.)");
		
		// Note efficiency on IO...
		logger.info("   - " + mBytesRead + " bytes read, " + mBytesEmitted + " bytes emitted." +
				"(factor of " + (mBytesEmitted/mBytesRead) + " advantage)");
		logger.info("   - " + mBytesWritten + " bytes written, " + mBytesSubmitted + " bytes submitted." + 
				"(factor of " + (mBytesSubmitted/mBytesWritten) + " advantage)");
		
	}
}

class CacheEntry
{
	CacheEntry mLRUNext, mLRUPrev;
	int        mID;
	String     mValue;
}