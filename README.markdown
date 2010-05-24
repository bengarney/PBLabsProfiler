PBLabsProfiler Beta
===================

This is a utilitarian profiler for use with Flash. It works with the
[flash.sampler](http://livedocs.adobe.com/flash/9.0/ActionScriptLangRefV3/flash/sampler/package-detail.html) API.

Prerequisites
-------------

You need to be on a supported OS. Currently the prebuilt binary only works on OS X. (See the *Hacking PBLabsProfiler* section below for details on building for other platforms.)

You need to have a recent JRE installed. We have been using Java 2 Runtime Edition 1.5. If you are on OS X you already have this.

Running The Binary
------------------

If you have received a prepackaged ZIP with a JAR in it, you do not need to compile any files to use the profiler. Follow these steps:
 
1. Unzip the PBLabsProfiler.zip to a location of your choosing.
2. Open the terminal and run these commands:

	`chmod u+x PBLabsProfiler.sh`
	
	`./PBLabsProfiler.sh`

3. Confirm that the PBLabsProfiler app has come up and shown its UI. You should see output like the following:

    `INFO: PBLabs Profiler server started on localhost:4262`

4. Close all web browsers and Flash Player instances.
5. Find [mm.cfg](http://www.adobe.com/devnet/flashplayer/articles/flash_player_admin_guide/flash_player_admin_guide.pdf) (see section 3), and:
  * Add a line like: `PreloadSwf=/path/to/PBLabsProfiler/Agent.swf`
  * You might want to turn on logging to a file, it makes debugging the stub a great deal simpler. Mine reads like this:
  
    `PreloadSwf=/path/to/PBLabsProfiler/Agent.swf`

    `TraceOutputFileEnable=1`

    `ErrorReportingEnable=1`

    `MaxWarnings=1000000`

    `TraceOutputBuffered=1`

6. Add *Agent.swf* to the list of trusted files using the Flash Player Settings Manager [here](http://www.macromedia.com/support/documentation/en/flashplayer/help/settings_manager04a.html#119065)
7. Open Flash content with the Flash debug player active/installed. The profiler will automatically open new windows for each profiled SWF. Currently closing windows results in a crash.

Hacking on PBLabsProfiler
=========================

If you want to compile this tool from scratch or develop it, please read the following.

The AS3 profiling stub is based on http://github.com/osi/flash-profiler. The Java client is written from scratch using [SWT](http://eclipse.org/swt/).

Prerequisites
-------------

* [Flex SDK 4.0.0](http://opensource.adobe.com/wiki/display/flexsdk/Flex+SDK)
* [Eclipse Java](http://www.eclipse.org/) development environment. You don't need the EE version.

Compile
-------

1. Compile Agent.as to Agent.swf. I use: 

	`mxmlc Agent.as -target-player=10`

2. Compile and run the Java project using Eclipse. FlashProfiler.java is the main class. Add all the .jars to your class path. You may need to get the appropriate version of SWT for your platform.

Usage
-----

1. Launch the profiler from Eclipse. You may have to pass the -XstartOnFirstThread command line/VM option for it to work on OS X.  -d32 may also be required if you have  64bit VM.
2. Continue with the first set of usage instructions starting with step 4.
