// Licensed under the Apache Software License v2
// Text available at http://www.apache.org/licenses/LICENSE-2.0
// Copyright 2009 by Peter Royal <peter.royal@pobox.com>
// Copyright 2010 by Ben Garney <ben.garney@gmail.com>
// Compile with:
// /Applications/Adobe\ Flash\ Builder\ 4.5/sdks/4.5.0/bin/mxmlc -static-link-runtime-shared-libraries -debug Agent.as
// or similar.
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
   import flash.utils.*;

   public class Agent extends Sprite
   {
      private static const PREFIX:String = "[AGENT]";

      private var _sampleSender:Timer;

      private var _baseTime:Number;

      private var _samplingStarted:Number;

      private var _sampling:Boolean = false;

      public var lastReportTime:int = 0;

      public function Agent()
      {
         trace(PREFIX, "Loaded PBLabsProfiler Agent.");

         _sampleSender = new Timer(16);
         _sampleSender.addEventListener(TimerEvent.TIMER, samplePump);
         _sampleSender.start();

         _sampling = true;
         setSamplerCallback(samplePump);
         startSampling();
         
         samplePump();
      }

      private function samplePump(e:* = null):void
      {
          trace(PREFIX, "Considering samples.");
          
         // Pause sampling.
         var wasSampling:Boolean = _sampling;
         if (_sampling)
         {
            pauseSampling();
           // trace(PREFIX, "Pausing sampling.");
        }

        // Print a report.
        if(true) //getTimer() - lastReportTime > 5000)
        {
            printReport();
            lastReportTime = getTimer();
        }

         // Dump acquired data.
         grabSamples();

         // Resume sampling.
         if (wasSampling)
         {
             //trace(PREFIX, "Resuming sampling.");
            startSampling();
         }
      }
      
      private function printReport():void
      {
          trace(PREFIX, "Total:");
          var totalSum:int = 0;
          for(var key:String in totalAllocated)
          {
              if(totalAllocated[key] == 0 || isNaN(totalAllocated[key]))
                 continue;
                 
             trace(PREFIX, "  " + key + " " + totalAllocated[key] + "    " + (totalAllocatedSize[key] / 1024.0).toFixed(2) + "kb");
             totalSum += totalAllocatedSize[key];
          }
          trace(PREFIX, "   Total size = " + (totalSum / 1024.0).toFixed(2) + "kb")
          trace(PREFIX, "Delta:");
          var deltaSum:int = 0;
          for(key in totalDelta)
          {
              if(totalDelta[key] == 0 || isNaN(totalDelta[key]))
                 continue;
                 
              trace(PREFIX, "  " + key + " " + totalDelta[key] + "    " + (totalDeltaSize[key] / 1024.0).toFixed(2) + "kb");
              deltaSum += totalDeltaSize[key];
              totalDelta[key] = 0;
              totalDeltaSize[key] = 0;
          }          
          trace(PREFIX, "   Delta size = " + (deltaSum / 1024.0).toFixed(2) + "kb")
      }
      
      public var outstandingAllocs:Dictionary = new Dictionary();
      public var outstandingAllocSize:Dictionary = new Dictionary();
      public var totalAllocated:Dictionary = new Dictionary();
      public var totalAllocatedSize:Dictionary = new Dictionary();
      public var totalDelta:Dictionary = new Dictionary();
      public var totalDeltaSize:Dictionary = new Dictionary();

      private function grabSamples():void
      {
         // Called when we are about to overflow sampling storage.
         // So shove it somewhere and keep going... NEVER SURRENDER NEVER RETREAT!
         var samples:* = getSamples();
         for each (var s:Sample in samples)
         {
             var ds:DeleteObjectSample = s as DeleteObjectSample;
             var ns:NewObjectSample = s as NewObjectSample;
            if (ds)
            {
                totalAllocated[outstandingAllocs[ds.id]]--;
                if(outstandingAllocSize[ds.id] != ds.size)
                   trace(PREFIX, "Saw deletion of " + outstandingAllocs[ds.id] + " at size " + ds.size + " but created at size " + outstandingAllocSize[ds.id])

                outstandingAllocs[ds.id] = null;
                delete outstandingAllocs[ds.id];

                totalAllocatedSize[typeKey] -= ds.size;
            }
            else if (ns)
            {
               // Get type and compensate for activation objects.
               var typeKey:String = ns.type.toString();
               if(ns.stack && ns.stack[0].toString() == "[activation-object]()")
                    typeKey = "[activation-object]";
               
                // Sanity check for dupe ids.
                if(outstandingAllocs[ns.id])
                   trace(PREFIX, "Saw new sample for " + ns.id + " twice, old val was " + outstandingAllocs[ns.id] + " new val was " + typeKey);

               // Note type for later accounting.
               outstandingAllocs[ns.id] = typeKey;
               outstandingAllocSize[ns.id] = ns.size;
               
               // Update total counts + sizes.
               if(totalAllocated[typeKey] == null || isNaN(totalAllocated[typeKey]))
                    totalAllocated[typeKey] = 0;
               if(totalDelta[typeKey] == null || isNaN(totalDelta[typeKey]))
                    totalDelta[typeKey] = 0;
               if(totalDeltaSize[typeKey] == null || isNaN(totalDeltaSize[typeKey]))
                    totalDeltaSize[typeKey] = 0;
               if(totalAllocatedSize[typeKey] == null || isNaN(totalAllocatedSize[typeKey]))
                     totalAllocatedSize[typeKey] = 0;

               totalAllocated[typeKey]++;
               totalDelta[typeKey]++;

               totalAllocatedSize[typeKey] += ns.size;
               totalDeltaSize[typeKey] += ns.size;
            }
         }
         trace("Clearing samples.");
         clearSamples();
      }
   }
}


/*
if(typeKey == "[class BlobDatabase]")
{
    trace(PREFIX, "Saw BD!")
    trace(PREFIX, "Size = " + ns.size);
    trace(PREFIX, "object = " + ns.object);
    trace(PREFIX, "type = " + ns.type);
    trace(PREFIX, "stack = " + ns.stack);
    trace(PREFIX, "stack[0] = " + ns.stack[0]);
}
*/
