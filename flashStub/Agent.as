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
   import flash.display.BitmapData;
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
         sampleInternalAllocs(false);
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
        if(getTimer() - lastReportTime > 5000)
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
          var totalSize:Number = 0, totalCount:int = 0;
          var reportMapSize:Dictionary = new Dictionary();
          var reportMapCount:Dictionary = new Dictionary();

          for(var id:* in allocatedIdToNewSample)
          {
              var typeKey:String = allocatedIdToTypeMap[id];
              if(!reportMapSize[typeKey])
                reportMapSize[typeKey] = 0;
              if(!reportMapCount[typeKey])
                reportMapCount[typeKey] = 0;

              if(allocatedIdToNewSample[id].object == null)
              {
                  trace(PREFIX, "Discarding sample #" + id + " due to null object.");
                  delete allocatedIdToNewSample[id];
                  continue;
              }
              reportMapSize[typeKey] += getSize(allocatedIdToNewSample[id].object);
              reportMapCount[typeKey] ++;

              if(allocatedIdToNewSample[id].object is BitmapData)
              {
                var bd:BitmapData = allocatedIdToNewSample[id].object as BitmapData;
                try
                {
                  reportMapSize[typeKey] += bd.width * bd.height * 4;                  
                }
                catch(e:*) 
                {
                  trace(PREFIX, "Unable to tally bitmapdata with id " + id);
                }
              }
              
              totalCount++;
          }
          
          var reportList:Array = new Array();
          
          for(var type:String in reportMapSize)
          {
              totalSize += reportMapSize[type];
              reportList.push("   " + type + " " 
                  + (reportMapSize[type] / 1024.0).toFixed(2) + "kb   "
                  + reportMapCount[type]);
          }

          trace(PREFIX, "Memory Usage Report:");
          reportList.sort();
          for(var i:int=0; i<reportList.length; i++)
                trace(PREFIX, reportList[i]);
          trace(PREFIX, " Total = " + (totalSize / 1024.0).toFixed(2) + "kb, churn = " + (churn / 1024.0).toFixed(2) + "kb, count = " + totalCount);
          churn = 0;
      }
      
      public var churn:int = 0;
      public var allocatedIdToTypeMap:Dictionary = new Dictionary();
      public var allocatedIdToNewSample:Dictionary = new Dictionary();

      private function grabSamples():void
      {
         // Called when we are about to overflow sampling storage.
         // So shove it somewhere and keep going... NEVER SURRENDER NEVER RETREAT!
         var samples:* = getSamples();
         for each (var s:Sample in samples)
         {
            var ds:DeleteObjectSample = s as DeleteObjectSample;
            if (ds)
            {
                if(allocatedIdToNewSample[ds.id] == null)
                {
                    churn += ds.size;
                    trace(PREFIX, "Got deletion for object that was never recorded: " + ds.id);
                }
                else if(allocatedIdToNewSample[ds.id].object == null)
                {
                    churn += ds.size;                    
                }
                
                delete allocatedIdToTypeMap[ds.id];
                delete allocatedIdToNewSample[ds.id];
            }
            
            var ns:NewObjectSample = s as NewObjectSample;
            if (ns)
            {
               // Get type and compensate for activation objects.
               var typeKey:String = ns.type ? ns.type.toString() : "[internal]";
               if(ns.stack && ns.stack[0].toString() == "[activation-object]()")
                    typeKey = "[activation-object]";

               if(allocatedIdToNewSample[ns.id] != null)
                   trace(PREFIX, "Got new sample for id " + ns.id + " twice.");
               
               allocatedIdToTypeMap[ns.id] = typeKey;
               allocatedIdToNewSample[ns.id] = ns;
            }
         }
         
         trace("Clearing samples.");
         clearSamples();
      }
   }
}
