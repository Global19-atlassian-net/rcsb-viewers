<?php
  session_start();
  set_include_path($_SESSION['INCLUDE_PATH']);
  include_once "resources/snippets/prefix.php";
?>
<p>
The <em>RCSB MBT Lib and Structure Viewers</em> comprise a body of code that allows the programmer
to build tools and viewers for the analyses and viewing of protein structures.  Projects may be divided
into two basic categories:
<ul>
<li>Support libraries and frameworks</li>
<li>Viewer applications</li>
</ul>
<p>
A further overview of these divisions follows.</p>
<h2>Support Libraries and Frameworks</h2>
<p>
These projects provide the underpinnings for creating applications.</p>
<dl>
<dt>3rd Party Libs</dt>
<dd>
These are external support jars (that are not included in the standard JRE distribution) 
the rest of the framework relies on.  Key jar subsystems are:
<ul>
<li>JOGL - Java <em>Open GL</em> implementation.</li>
<li>JAI - Java <em>Advanced Imaging</em> implementation.</li>
</ul>
In addition to the jars, the JOGL implementation requires JNI native libraries for each targed
platform, the locations of which must be specified in execution directives.</dd>
<dt>RCSB MBT Lib</dt>
<dd>
This is the <em>Molecular Biology Toolkit</em>, a framework and structure specification that provides
the foundation for creating and accessing structure models.  In addition, the toolkit provides
low-level base classes for application creation, basic UI services (file open dialog, etc.), and 3d
scene creation.</dd>
<dd>
The framework can provide the foundation for viewers (as it currently does), but parts of it may be
used to construct non-viewer/non- or limited- UI applications, or even command-line or out-of-process
analysis utilities.</dd>
<dt>RCSB Viewers Framework</dt>
<dd>
The <em>Viewers Framework</em> is an intermediate project between the viewers and the MBT.  It contains
modules that are shared between the viewers, but that are more 'viewer-specific' than the general
functionality provided in the MBT.</dd>
</dl>
<h2>Applications</h2>
<p>
These are the actual implementing applications, currently consisting of the suite of 3d structure
viewers:
<dl>
<dt>RCSB Simple Viewer</dt>
<dd>
A viewer that takes up the entire mainframe (window), without any additional panels or other control
mechanism.  It simply displays the structure.</dd>
<dd>
A rudimentary menu is provided to open other structures, and a status bar is provided in the mainframe
to echo the results of component hovers or other status information.</dd>
<dt>RCSB Protein Workshop</dt>
<dd>
A viewer that provides a control panel, allowing view modifications such as rendering styles, colors,
visibility, etc.</dd>
<dt>RCSB Ligand Explorer</dt>
<dd>
Displays a structure and ligand combination, in the same space.  Various tools are provided in a
control panel to explore relationships between the ligand and associated structure.</dd>
<dt>RCSB PDB Kiosk</dt>
<dd>
A unique 'outreach' viewer that displays a number of structures in sequence, animating between
different aspects of views.  A kind of 'moving slideshow' presentation.</dd>
</dl>
<h1>Re-Architecture Effort</h1>
<p>
The MBT was created in 1998, using Java 1.0 constructs.  Since then, there have been many improvements
in the language in terms of performance, type-safety, and syntax.  Furthermore, the
<em>OpenGL</em> implementation is based on the 1.0 standard.  Similar improvements have occured 
in the <em>OpenGL</em> implementation, as well.</p>
<p>
As a result, this version (undertaken in 2008), represents a large effort to re-architect the MBT and
Viewers (which had
diverged considerably in implementation), with an eye to a more maintainable condition, and one that
more cleanly follows known architectural constructions.</p>
<p>
There has also been a considerable effort to upgrade many of the constructs to minimally the JRE 1.5
specification, especially as regards type-safety in constructs.</p>
<p class="newidea">
There has been no intent to address or upgrade <em>OpenGL</em> usage in this version.</p>
<<?php
  include_once "resources/snippets/suffix.php";
?>