// Licensed under the Apache Software License v2
// Text available at http://www.apache.org/licenses/LICENSE-2.0
// Copyright 2009 by Peter Royal <peter.royal@pobox.com>
// Copyright 2010 by Ben Garney <ben.garney@gmail.com>
package
{

   import flash.display.Sprite;
   import flash.events.DataEvent;
   import flash.events.Event;
   import flash.events.IOErrorEvent;
   import flash.events.ProgressEvent;
   import flash.events.SecurityErrorEvent;
   import flash.events.TimerEvent;
   import flash.net.Socket;
   import flash.sampler.*;
   import flash.system.System;
   import flash.utils.ByteArray;
   import flash.utils.Timer;
   import flash.utils.getQualifiedClassName;
   import flash.utils.getTimer;

   public class Agent extends Sprite
   {
      private static const HOST:String = "localhost";

      private static const PORT:int = 42624;

      private static const PREFIX:String = "[AGENT]";

      private var _host:String;

      private var _port:int;

      private var _socket:Socket;

      private var _connected:Boolean;

      private var _sampleSender:Timer;

      private var _baseTime:Number;

      private var _samplingStarted:Number;

      private var _sampling:Boolean = false;

      public var pendingSamples:Array = [];

      private var cache:NetStringCache = new NetStringCache();

      public const MAX_INT:int = int.MAX_VALUE - 1;

      private var samplesSent:int = 0;

      private var _bitStream:BitStream = new BitStream(4000);

      private var bytesSent:int = 0;

      private var outputBytes:ByteArray = new ByteArray();

      public function Agent()
      {
         trace(PREFIX, "Loaded");

         _host = loaderInfo.parameters["host"] || HOST;
         _port = loaderInfo.parameters["port"] || PORT;

         _socket = new Socket();
         _socket.timeout = 240 * 1000;

         _socket.addEventListener(SecurityErrorEvent.SECURITY_ERROR, fail);
         _socket.addEventListener(IOErrorEvent.IO_ERROR, fail);
         _socket.addEventListener(ProgressEvent.SOCKET_DATA, dataReceived);
         _socket.addEventListener(Event.CONNECT, connected);
         _socket.addEventListener(Event.CLOSE, close);

         connect();

         _sampleSender = new Timer(16);
         _sampleSender.addEventListener(TimerEvent.TIMER, samplePump);
         _sampleSender.start();


      }

      private function samplePump(te:TimerEvent):void
      {
         var wasSampling:Boolean = _sampling;
         if (_sampling)
            pauseSampling();

         grabSamples();

         // See if we can send some samples out.
         if (_socket && _connected && pendingSamples.length > 0)
         {
            var numToSend:int = 5000 - 1;
            while (numToSend && pendingSamples.length)
            {
               var top:* = pendingSamples.pop();

               if (top is DeleteObjectSample)
               {
                  // Skip deletes, we don't care.
                  continue;
               }
               else
               {
                  // Write the message type
                  if (top is NewObjectSample)
                  {
                     _socket.writeShort(0x4210);
                  }
                  else if (top is DeleteObjectSample)
                  {                     //trace("Writing delete object sample");
                     _socket.writeShort(0x4211);
                  }
                  else
                  {
                     _socket.writeShort(0x4212);
                  }

                  // Write the sample using the BitStream
                  encodeSample(outputBytes, top, 0);
                  _socket.writeUnsignedInt(outputBytes.length);
                  _socket.writeBytes(outputBytes);

                  samplesSent++;
               }

               numToSend--;

               if (numToSend % 1000 == 0)
                  _socket.flush();
            }
            _socket.flush();
         }

         if (wasSampling)
            startSampling();
      }

      private function grabSamples():void
      {
         // Called when we are about to overflow sampling storage.
         // So shove it somewhere and keep going... NEVER SURRENDER NEVER RETREAT!
         for each (var s:Sample in getSamples())
         {
            if (s is DeleteObjectSample)
               continue;
            if (s is NewObjectSample)
            {
               if (s && s.stack && s.stack.length > 0)
               {
                  var frame:StackFrame = s.stack[0];
                  if (frame.name == "[abc-decode]")
                     continue;
                  if (frame.name.indexOf("mx.") == 0)
                     continue;
               }
            }
            pendingSamples.push(s);
         }

         clearSamples();
      }

      private function dataReceived(e:ProgressEvent):void
      {
         if (_socket.bytesAvailable < 2)
         {
            // All commands are two-bytes.
            return;
         }

         var command:uint = _socket.readUnsignedShort();

         trace(PREFIX, "Received command", command);

         switch (command)
         {
            case 0x4202:
               _socket.writeShort(0x4203);
               _socket.writeUnsignedInt(getTimer());
               _socket.writeUnsignedInt(System.totalMemory);
               _socket.flush();
               return;
            case 0x4204:
               startSampling();
               _sampling = true;
               _samplingStarted = new Date().getTime() * 1000;
               _socket.writeShort(0x4205);
               _socket.flush();
               return;
            case 0x4206:
               pauseSampling();
               _sampling = false;
               _socket.writeShort(0x4207);
               _socket.flush();
               return;
            case 0x4208:
               stopSampling();
               clearSamples();
               _sampling = false;
               _socket.writeShort(0x4209);
               _socket.flush();
               return;

            default:
               _socket.writeShort(0x4200);
               _socket.writeUTF("UNKNOWN COMMAND");
               _socket.flush();
               return;
         }
      }

      private function encodeSample(out:ByteArray, s:Sample, sampleTimeOffset:Number):void
      {
         var time:Number = _baseTime * 1000 + (s.time - sampleTimeOffset);

         _bitStream.reset();
         out.position = 0;

         _bitStream.stringCache = cache;

         if (s is NewObjectSample)
         {
            var nos:NewObjectSample = s as NewObjectSample;
            _bitStream.writeRangedInt(pendingSamples.length, 0, MAX_INT);
            _bitStream.writeRangedInt(time, 0, MAX_INT);
            _bitStream.writeRangedInt(nos.id, 0, MAX_INT);
            _bitStream.writeCachedString(getQualifiedClassName(nos.type) + "");
         }
         else if (s is DeleteObjectSample)
         {
            /*
               var dos:DeleteObjectSample = s as DeleteObjectSample;

               _bitStream.writeRangedInt(pendingSamples.length, 0,MAX_INT);
               _bitStream.writeRangedInt(time, 0,MAX_INT);
               _bitStream.writeRangedInt(dos.id, 0,MAX_INT);;
               _bitStream.writeRangedInt(dos.size, 0,MAX_INT);
             */
            throw new Error("not implemented");

         }
         else
         {

            _bitStream.writeRangedInt(pendingSamples.length, 0, MAX_INT);
            _bitStream.writeRangedInt(time, 0, MAX_INT);
         }

         var stack:Array = s.stack;

         if (null == stack)
         {
            _bitStream.writeRangedInt(0, 0, MAX_INT);
         }
         else
         {
            _bitStream.writeRangedInt(stack.length, 0, MAX_INT);

            for each (var frame:StackFrame in stack)
            {
               _bitStream.writeCachedString(frame.name + "");

               if (frame.file == null)
               {
                  _bitStream.writeCachedString("empty");
                  _bitStream.writeRangedInt(0, 0, MAX_INT);
               }
               else
               {
                  _bitStream.writeCachedString(frame.file);
                  _bitStream.writeRangedInt(frame.line, 0, MAX_INT);
               }
            }
         }

         _bitStream.writeCachedString("SAMPLE-DONE");

         var numBytes:int = _bitStream.getLengthInBytes();

         bytesSent += numBytes;

         _bitStream.getByteArray().readBytes(out, 0, numBytes);
      }

      private function connect():void
      {
         trace(PREFIX, "Trying to connect to", _host, ":", _port);

         try
         {
            _socket.connect(_host, _port);
         }
         catch (e:Error)
         {
            trace(PREFIX, "Unable to connect", e);
         }
      }

      private function connected(e:Event):void
      {
         trace(PREFIX, "Connected");

         _baseTime = new Date().getTime();
         var seconds:uint = _baseTime / 1000;
         var timer:uint = getTimer();

         _socket.writeShort(0x4201);
         _socket.writeUnsignedInt(seconds);
         _socket.writeShort(_baseTime - (seconds * 1000));
         _socket.writeUnsignedInt(timer);
         _socket.flush();

         trace(PREFIX, _baseTime, seconds, _baseTime - (seconds * 1000), timer);

         _connected = true;
      }

      private function close(e:Event):void
      {
         _connected = false;

         trace(PREFIX, "Disconnected");
      }

      private function fail(e:Event):void
      {
         trace(PREFIX, "Communication failure", e);

         _socket.close();
         _connected = false;
      }
   }
}

