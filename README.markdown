PBLabsProfiler
==============

This is a utilitarian profiler for use with Flash. It works with the
[flash.sampler](http://livedocs.adobe.com/flash/9.0/ActionScriptLangRefV3/flash/sampler/package-detail.html) API.

The AS3 profiling stub is based on http://github.com/osi/flash-profiler. The Java client is not.

Prerequisites
-------------

1. [Flex SDK 4.0.0](http://opensource.adobe.com/wiki/display/flexsdk/Flex+SDK)
2. Eclipse java development environment.

Compile
-------

1. Compile Agent.as to Agent.swf. I use: mxmlc Agent.as -target-player=10
2. Compile and run the Java project. FlashProfiler.java is the main class. Add all the .jars to your class path. You may need to get the appropriate version of SWT for your platform.

Usage
-----

1. Find [mm.cfg](http://www.adobe.com/devnet/flashplayer/articles/flash_player_admin_guide/flash_player_admin_guide.pdf) (see section 3), and:
  * Add a line like: `PreloadSwf=/path/to/Agent.swf`
  * You might want to turn on logging to a file, it makes debugging the stub a great deal simpler. Mine reads like this:
  
    TraceOutputFileEnable=1
    ErrorReportingEnable=1
    MaxWarnings=1000000
    TraceOutputBuffered=1

2. Add *Agent.swf* to the list of trusted files using the Flash Player Settings Manager [here](http://www.macromedia.com/support/documentation/en/flashplayer/help/settings_manager04a.html#119065)
3. Launch the FlashProfiler Java application. You may have to pass the -XstartOnFirstThread command line/VM option for it to work on OS X.
4. Open Flash content with the Flash debug player active. The profiler will automatically open new windows for each profiled SWF.
