package 
{
   import flash.utils.Dictionary;
  
   /**
    * Caches strings so that in most cases a short cache ID is sent over the wire
    * instead of the full string. Uses an LRU cache eviction policy.
    * 
    * Use read() and write() to read and write cached strings to a BitStream.
    * 
    * BitStream has methods to get/set an associated NetStringCache. Most of the
    * time you will use the CachedStringElement in a NetRoot to read/write
    * cached strings.
    * 
    * Network protocols will often need to send identifiers, for instance to
    * indicate the type of an event or object. Synchronizing hardcoded IDs is
    * a big maintenance pain. Some systems even assign IDs based on the order
    * that classes are encounted in a compiled binary!
    * 
    * Using cached strings is nearly as efficient and much simpler. Commonly
    * used identifiers are assigned IDs and sent in just a few bits. LRU caching
    * means that the "hot" strings are always in the cache.
    * 
    * Note that some data will be more usefully sent as uncached strings. Chat
    * messages for instance are rarely the same, and will just pollute the cache
    * so it is better to send them uncached. 
    * 
    * The format on the wire for a cached string is as follows. First, a bit
    * indicating if we are transmitting a new string or reusing an existing
    * cached item. If the bit is true, then an integer cache ID is written 
    * (mStringRefBitCount bits in size), followed by a string written using
    * BitStream.writeString(). If the bit is false, then only a cache ID is 
    * written.
    * 
    * In other words the protocol looks like this:
    *    [1 bit flag][mStringRefBitCount bit cache ID][optional string]
    */  
   public class NetStringCache
   {
      private static const PREFIX:String = "[AGENT]";
      
      /**
       * @param bitCount Number of bits to use for string cache references. The
       *                 size of the cache will be 2**bitCount.
       */ 
      public function NetStringCache(bitCount:int = 10)
      {
         stringRefBitCount = bitCount;
         entryCount = 1 << stringRefBitCount;
         
         idLookupTable         = new Array(entryCount);
         stringHashLookupTable = new Dictionary();
         cacheEntries          = new Array(entryCount);
         
         // Sentinels for start/end of list.
         lruHead = new CacheEntry();
         lruTail = new CacheEntry();
         
         // Fill in all our cache entries.
         for(var i:int=0; i<entryCount; i++)
         {
            var curEntry:CacheEntry = cacheEntries[i] = new CacheEntry();
            
            // Get the preceding entry.
            var prevEntry:CacheEntry;
            if(i==0)
               prevEntry = lruHead;
            else
               prevEntry = cacheEntries[i-1];
            
            // Now set up links.
            curEntry.lruPrev = prevEntry;
            curEntry.lruNext = lruTail;
            prevEntry.lruNext = curEntry;
            
            // Fill in the ID.
            curEntry.id = i;
            idLookupTable[i] = curEntry;
         }
         
         // Set up the end sentinel.
         lruTail.lruPrev = cacheEntries[entryCount-1];
      }
      
      /**
       * Read a string from a BitStream, updating internal cache
       * state as appropriate.
       * @param bs
       * @return
       */
      public function read(bs:BitStream):String
      {
         readCount++;
         
         var readString:String = null;
         
         // First, check if this is an update or a cached entry.
         if(bs.readFlag())
         {

            // This is some new data...
            var startRead:int = bs.currentPosition;
            
            // Read the ID we're overwriting.
            var newId:int = bs.readInt(stringRefBitCount);
            
            // And the string.
            readString = bs.readString();
            
            bytesRead += Number(bs.currentPosition / startRead) / 8.0;
            
            // Overwrite the cache.
            reuseEntry(lookupById(newId), readString);
         }
         else
         {
            // This is referring to cached data...
            var oldId:int = bs.readInt(stringRefBitCount);
            
            // Look it up in our cache.
            readString = lookupById(oldId).value;
            
            bytesRead += Number(stringRefBitCount) / 8.0;
            
            cachedReadCount++;
         }
         
         if(readString)
            bytesEmitted += readString.length;
         
         return readString;
      }
      
      /**
       * Write a string from a BitStream, updating internal cache
       * state as appropriate.
       * @param bs
       * @return
       */
      public function write(bs:BitStream, s:String):void
      {
         if(!s)
            throw new Error("You must pass a string to NetStringCache write.");
         
         writeCount++;
         bytesSubmitted += s.length;
         
         // Find the string in our hash.
         var ce:CacheEntry = lookupByString(s);
         var found:Boolean = true;
         
         if(ce == null)
         {
            // Don't know about this string, so set it up (and note we did so).
            found = false;      
            ce = getOldestEntry();
            reuseEntry(ce, s);
         }   
         
         // Note the ID.
         var foundId:int = ce.id;         
         
         // Is it new?
         if(bs.writeFlag(!found))
         {
            // Write the ID, and the string.
            bs.writeInt(foundId, stringRefBitCount);         
            bs.writeString(s);
            
            bytesWritten += (Number(stringRefBitCount) / 8.0) + s.length;            
         }
         else
         {
            // Great, write the ID and we're done.
            bs.writeInt(foundId, stringRefBitCount);
            
            cachedWriteCount++;
            bytesWritten += (stringRefBitCount / 8.0);
         }
      }
      
      /**
       * Take passed CacheEntry and bring it to the front of our cache.
       * @param ce
       */
      private function bringToFront(ce:CacheEntry):void
      {
         // Unlink it from where it is the list...
         ce.lruPrev.lruNext = ce.lruNext;
         ce.lruNext.lruPrev = ce.lruPrev;
         
         // And link it at the head.
         ce.lruNext = lruHead.lruNext;
         ce.lruNext.lruPrev = ce;
         ce.lruPrev = lruHead;
         lruHead.lruNext = ce;
      }
      
      /**
       * Get the oldest entry, probably so you can overwrite it.
       */
      private function getOldestEntry():CacheEntry
      {
         return lruTail.lruPrev;
      }
      
      /**
       * Remove an entry from secondary data structures, assign a new string
       * to it, and reinsert it. 
       */
      private function reuseEntry(ce:CacheEntry, newString:String):void
      {
         // Get it out of the string map.
         if(ce.value != null)
         {
            stringHashLookupTable[ce.value] = null;
            delete stringHashLookupTable[ce.value];
         }
         
         // Assign the new string.
         ce.value = newString;
         
         // Reinsert string map.
         stringHashLookupTable[newString] = ce;
         
         // Bring to front of cache.
         bringToFront(ce);
      }
      
      /**
       * Look up entry by string.
       */
      private function lookupByString(s:String):CacheEntry
      {
         return stringHashLookupTable[s];
      }
      
      /**
       * Look up entry by its id.
       */
      private function lookupById(id:int):CacheEntry
      {
         return idLookupTable[id];
      }
      
      /**
       * Dump some statistics to the Logger.
       */ 
      public function reportStatistics():void
      {
         trace(PREFIX,"Usage Report");
         trace(PREFIX, "   - " + readCount + " reads, " + cachedReadCount + " cached (" 
            + (cachedReadCount / readCount)*100.0 + "% cached.)");
         trace(PREFIX, "   - " + writeCount + " writes, " + cachedWriteCount + " cached (" 
            + (cachedWriteCount / writeCount)*100.0 + "% cached.)");
         
         // How much of the cache is used?
         var numUsed:int = 0;
         for(var i:int=0; i<entryCount; i++)
         {
            if(idLookupTable[i] == null || idLookupTable[i].mValue == null)
               continue;
            
            numUsed++;
         }
         
         trace(PREFIX, "   - " + numUsed + " out of " + entryCount + " IDs in use (" 
            + (Number(numUsed) / Number(entryCount))*100.0 + "% utilization.)");
         
         // Note efficiency on IO...
         trace(PREFIX, "   - " + bytesRead + " bytes read, " + bytesEmitted + " bytes emitted." +
            "(factor of " + (bytesEmitted/bytesRead) + " advantage)");
         trace(PREFIX, "   - " + bytesWritten + " bytes written, " + bytesSubmitted + " bytes submitted." + 
            "(factor of " + (bytesSubmitted/bytesWritten) + " advantage)");
         
      }
      
      
      /**
       * Number of bits to use for encoding string references.
       */
      private var stringRefBitCount:int = 10;
      private var entryCount:int;
      private var idLookupTable:Array;
      private var stringHashLookupTable:Dictionary;
      private var cacheEntries:Array;
      
      private var lruHead:CacheEntry; 
      private var lruTail:CacheEntry;
      
      private var writeCount:int; 
      private var cachedWriteCount:int;
      private var readCount:int;
      private var cachedReadCount:int;
      private var bytesSubmitted:Number; 
      private var bytesWritten:Number;
      private var bytesEmitted:Number;
      private var bytesRead:Number;
      
   }
   
}

class CacheEntry
{
   public var lruNext:CacheEntry, lruPrev:CacheEntry;
   public var id:int;
   public var value:String;
}