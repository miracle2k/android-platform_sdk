This file describes the "gscripts" folder in ADT (Android Eclipse Plugin).


----------
- Overview
----------

ADT is the Android Eclipse Plugin. The plugin delivers a new editor, called
the the Graphical Layout Editor (a.k.a. GLE2), to visually edit Android layout
XML files.

Details on how to handle the various Android views and layouts is not
hardcoded in the GLE2 itself. Instead it is differed to a bunch of Groovy
scripts.


(TODO: expand/replace with a better overview of implementation... goal is
to use this a doc for 3rd-party projects to implement their own rules.)



-------------
- Groovy tips
-------------


- Debugging:

If you run ADT in debug mode and want to trace into Groovy
methods, you need to tell Eclipse where to find the Groovy source code.

To do this:
- in Eclipse, import an existing project
- Select the project at <android-source-tree>/prebuilt/common/groovy/
- This will add a new Eclipse project named "GroovySrc" which contains
  a single zip file with the groovy source.
- ADT is already pre-configured to find the Groovy source in the GroovySrc
  project.



- Private methods:

Be careful when adding new helper methods in the BaseView
or BaseLayout classes.

Due to the way Groovy looks up methods, private methods will *not* be found by
same-class methods if invoked by a derived class in the context of a closure
(which is about the case of all these helper methods.)
