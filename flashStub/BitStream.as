package
{
   import flash.errors.*;
   import flash.utils.*;
   
   /**
    * Class to perform bit-level reads and writes.
    */
   public class BitStream
   {
      /**
       * Constructor. You can initialize from an array of bytes, a number
       * specifying length in bytes, or a ByteArray (which will be modified
       * by any BitStream operations).
       */
      public function BitStream(data:*)
      {
         if(data is Array)
         {
            // Set our length.
            bits.length = data.length;
            totalBits = BYTE_LENGTH * data.length;
            
            // Copy data into our byte array.
            for(var i:int=0; i<data.length; i++)
               bits[i] = data[i];
         }
         else if(data is Number)
         {
            // Set the length.
            bits.length = data;
            totalBits = BYTE_LENGTH * data;
         }
         else if(data is ByteArray)
         {
            // Copy the data. TODO: Do we need a deep copy?
            bits = data;
            totalBits = BYTE_LENGTH * data.length;
         }
         else
         {
            throw Error("BitStream expects an Array of bytes or a size in bytes or a ByteArray.");
         }
      }
      
      /**
       * Write a bit to the stream and advance to the next bit.
       */
      public function writeFlag(value:Boolean):Boolean
      {
         if(currentBit >= totalBits)
            throw new EOFError("Out of bits!");
         
         if(value)
            bits[currentBit>>3] |= (1 << (currentBit & 0x7));
         else
            bits[currentBit>>3] &= ~(1 << (currentBit & 0x7));
         
         currentBit++;
         
         return value;
      }
      
      /**
       * Read a bit from the stream and advance to the next bit.
       */
      public function readFlag():Boolean
      {
         if(currentBit >= totalBits)
            throw new EOFError("Out of bits!");
         
         var b:int = bits[currentBit>>3];
         return ((b >> (currentBit++ & 0x7)) & 0x1) == 1;
      }
      
      /**
       * Write an 8-bit byte to the stream.
       */
      public function writeByte(value:int):void
      {
         for(var i:int=0; i<BYTE_LENGTH; i++)
            writeFlag( ((value>>i)&1) == 1 );         
      }
      
      /**
       * Read an 8-bit byte from the stream.
       */
      public function readByte():int
      {
         var b:int = 0;
         for(var i:int=0; i<BYTE_LENGTH; i++)
            b |= boolToInt(readFlag()) << i;
         
         return b;
      }
      
      /**
       * Write a signed int with the specified number of bits.
       * 
       * <p>The value written must range from 0..2**bitCount. value
       * is treated as if it is masked against (2**bitCount - 1).</p>
       */
      public function writeInt(value:int, bitCount:int):void
      {
         //Logger.Print(this, "Writing " + value + " at " + CurrentPosition + " with " + bitCount + " bits");
         
         for(var i:int=0; i<bitCount; i++)
            writeFlag( ((value>>i)&1) == 1 );
      }
      
      /**
       * Read a signed int with the specified number of bits.
       * 
       * @see writeInt
       */
      public function readInt(bitCount:int):int
      {
         var cp:int = currentPosition;
         
         var val:int = 0;
         for(var i:int=0; i<bitCount; i++)
            val |= boolToInt(readFlag()) << i;
         
         //Logger.Print(this, "Read " + val + " at " + cp + " with " + bitCount + " bits");
         
         return val;
      }
      
      /**
       * Helper function to convert a Boolean to an int.
       */
      static public function boolToInt(b:Boolean):int
      {
         return b ? 1 : 0;
      }
      
      /**
       * Write a UTF8 string.
       * 
       * <p>The format is a 10 bit length specified in bytes, followed by that many
       * bytes encoding the string in UTF8.</p> 
       */
      public function writeString(s:String):void
      {
         // This is kind of a hack.
         var ba:ByteArray = new ByteArray();
         ba.writeUTF(s);
         ba.position = 0;
         
         writeInt(ba.readShort(), 10);
         while(ba.bytesAvailable)
            writeByte(ba.readByte());
      }
      
      public function getLengthInBytes():int
      {
         return (currentBit+7) >> 3;
      }
      
      /**
       * Read a string from the bitstream.
       * 
       * @see writeString()
       */
      public function readString():String
      {
         var lenInBytes:int = readInt(10);
         
         var ba:ByteArray = new ByteArray();
         ba.writeShort(lenInBytes);
         
         for(var i:int=0; i<lenInBytes; i++)
            ba.writeByte(readByte());
         
         ba.position = 0;
         
         return ba.readUTF();
      }
      
      /**
       * Write an integer value that can range from min to max inclusive. Calculates
       * required number of bits automatically.
       */
      public function writeRangedInt(v:int, min:int, max:int):void
      {
         var range:int = max - min + 1;
         var bitCount:int = getBitCountForRange(range);
         
         writeInt(v - min, bitCount);
      }
      
      /**
       * Read an integer that can range from min to max inclusive. Calculates required
       * number of bits automatically.
       */ 
      public function readRangedInt(min:int, max:int):int
      {
         var range:int = max - min + 1;
         var bitCount:int = getBitCountForRange(range);
         
         var res:int = readInt(bitCount) + min;
         
         if(res < min)
         {
            throw new Error("Read int that was below range! (" + res + " < " + min + ")");
         }
         else if(res > max)
         { 
            throw new Error("Read int that was above range! (" + res + " > " + max + ")");
         }
         return res;
      }
      
      
      
      /**
       * Write a float ranging from 0..1 inclusive encoded into the specified number
       * of bits.
       */ 
      public function writeFloat(value:Number, bitCount:int):void
      {
         writeInt((int)(value * ((1 << bitCount) - 1)), bitCount);
      }
      
      /**
       * Read a float ranged from 0 to 1 inclusive encoded into the specified number of bits.
       */
      public function readFloat(bitCount:int):Number
      {
         return Number(readInt(bitCount)) / Number((1 << bitCount) - 1);
      }
      
      /**
       * Have we reached the end of the stream?
       */
      public function get isEof():Boolean
      {
         return currentBit >= totalBits;
      }
      
      /**
       * Reset current position to start of stream.
       */ 
      public function reset():void
      {
         currentBit = 0;
         bits.position = 0;
      }
      
      /**
       * Position in the bit stream at which the next read or write will occur.
       * Reading or writing increments this position.
       */
      public function get currentPosition():int
      {
         return currentBit;
      }
      
      public function set currentPosition(pos:int):void
      {
         if(pos < 0 || pos >= totalBits)
            throw Error("Out of bounds!");
         
         currentBit = pos;
      }
      
      /**
       * How many bits of space left?
       */
      public function get remainingBits():int
      {
         return totalBits - currentBit;
      }
      
      /**
       * Get a reference to a ByteArray containing this BitStream's data.
       */
      public function getByteArray():ByteArray
      {
         return bits;
      }
      
      /**
       * Convenience property to allow a NetStringCache to be associated with a
       * BitStream; the BitStream doesn't use it itself.
       */ 
      public function set stringCache(sc:NetStringCache):void
      {
         _stringCache = sc;
      }
      
      public function get stringCache():NetStringCache
      {
         return _stringCache;
      }
      
      public function writeCachedString(str:String):void
      {
         _stringCache.write(this, str);   
      }
      
      /**
       * Read a byte, check that it has the expected value, and throw an exception with message if it does not.
       */
      public function assertByte(message:String, expectedByte:int):void
      {
         // Logger.print(this, "Checking assertion byte at " + currentPosition);
         
         var b:int = readByte();
         if(b != expectedByte)
            throw new Error("Mismatch: " + message + " (" + b + " != " + expectedByte + ")");
      }
      
      /**
       * Get number of bits required to encode values from 0..max.
       *
       * @param max The maximum value to be able to be encoded.
       * @return Bitcount required to encode max value.
       */
      public static function getBitCountForRange(max:int):int
      {
         // TODO: Make this use bits and be fast.
         return Math.ceil(Math.log(max) / Math.log(2.0));
      }


      private static var BYTE_LENGTH:int = 8;
      
      private var bits:ByteArray = new ByteArray();
      private var currentBit:int;
      private var totalBits:int;
      private var _stringCache:NetStringCache;
   }
}