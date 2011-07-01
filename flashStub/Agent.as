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
          trace(PREFIX, "Memory Usage Report:");
          
          var reportMap:Dictionary = new Dictionary();
          for(var id:* in allocatedIdToNewSample)
          {
              reportMap[allocatedIdToType[id]] += getSize(allocatedIdToNewSample[id].object);
          }
          
          for(var type:String in reportMap)
          {
              trace("   " + type + " " + (reportMap[type] / 1024.0).toFixed(2) + "kb");
          }
      }
      
      public var allocatedIdToType:Dictionary = new Dictionary();
      public var allocatedIdToNewSample:Dictionary = new Dictionary();

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
                delete allocatedIdToTypeMap[ns.id];
                delete allocatedIdToNewSample[ns.id];
            }
            else if (ns)
            {
               // Get type and compensate for activation objects.
               var typeKey:String = ns.type.toString();
               if(ns.stack && ns.stack[0].toString() == "[activation-object]()")
                    typeKey = "[activation-object]";
               
               allocatedIdToTypeMap[ns.id] = typeKey;
               allocatedIdToNewSample[ns.id] = ns;
            }
         }
         
         trace("Clearing samples.");
         clearSamples();
      }
   }
}
