/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.build;

import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AaptParser {

    // TODO: rename the pattern to something that makes sense + javadoc comments.
    /**
     * Single line aapt warning for skipping files.<br>
     * "  (skipping hidden file '&lt;file path&gt;'"
     */
    private final static Pattern sPattern0Line1 = Pattern.compile(
            "^\\s+\\(skipping hidden file\\s'(.*)'\\)$"); //$NON-NLS-1$

    /**
     * First line of dual line aapt error.<br>
     * "ERROR at line &lt;line&gt;: &lt;error&gt;"<br>
     * " (Occurred while parsing &lt;path&gt;)"
     */
    private final static Pattern sPattern1Line1 = Pattern.compile(
            "^ERROR\\s+at\\s+line\\s+(\\d+):\\s+(.*)$"); //$NON-NLS-1$
    /**
     * Second line of dual line aapt error.<br>
     * "ERROR at line &lt;line&gt;: &lt;error&gt;"<br>
     * " (Occurred while parsing &lt;path&gt;)"<br>
     * @see #sPattern1Line1
     */
    private final static Pattern sPattern1Line2 = Pattern.compile(
            "^\\s+\\(Occurred while parsing\\s+(.*)\\)$");  //$NON-NLS-1$
    /**
     * First line of dual line aapt error.<br>
     * "ERROR: &lt;error&gt;"<br>
     * "Defined at file &lt;path&gt; line &lt;line&gt;"
     */
    private final static Pattern sPattern2Line1 = Pattern.compile(
            "^ERROR:\\s+(.+)$"); //$NON-NLS-1$
    /**
     * Second line of dual line aapt error.<br>
     * "ERROR: &lt;error&gt;"<br>
     * "Defined at file &lt;path&gt; line &lt;line&gt;"<br>
     * @see #sPattern2Line1
     */
    private final static Pattern sPattern2Line2 = Pattern.compile(
            "Defined\\s+at\\s+file\\s+(.+)\\s+line\\s+(\\d+)"); //$NON-NLS-1$
    /**
     * Single line aapt error<br>
     * "&lt;path&gt; line &lt;line&gt;: &lt;error&gt;"
     */
    private final static Pattern sPattern3Line1 = Pattern.compile(
            "^(.+)\\sline\\s(\\d+):\\s(.+)$"); //$NON-NLS-1$
    /**
     * First line of dual line aapt error.<br>
     * "ERROR parsing XML file &lt;path&gt;"<br>
     * "&lt;error&gt; at line &lt;line&gt;"
     */
    private final static Pattern sPattern4Line1 = Pattern.compile(
            "^Error\\s+parsing\\s+XML\\s+file\\s(.+)$"); //$NON-NLS-1$
    /**
     * Second line of dual line aapt error.<br>
     * "ERROR parsing XML file &lt;path&gt;"<br>
     * "&lt;error&gt; at line &lt;line&gt;"<br>
     * @see #sPattern4Line1
     */
    private final static Pattern sPattern4Line2 = Pattern.compile(
            "^(.+)\\s+at\\s+line\\s+(\\d+)$"); //$NON-NLS-1$

    /**
     * Single line aapt warning<br>
     * "&lt;path&gt;:&lt;line&gt;: &lt;error&gt;"
     */
    private final static Pattern sPattern5Line1 = Pattern.compile(
            "^(.+?):(\\d+):\\s+WARNING:(.+)$"); //$NON-NLS-1$

    /**
     * Single line aapt error<br>
     * "&lt;path&gt;:&lt;line&gt;: &lt;error&gt;"
     */
    private final static Pattern sPattern6Line1 = Pattern.compile(
            "^(.+?):(\\d+):\\s+(.+)$"); //$NON-NLS-1$

    /**
     * 4 line aapt error<br>
     * "ERROR: 9-path image &lt;path&gt; malformed"<br>
     * Line 2 and 3 are taken as-is while line 4 is ignored (it repeats with<br>
     * 'ERROR: failure processing &lt;path&gt;)
     */
    private final static Pattern sPattern7Line1 = Pattern.compile(
            "^ERROR:\\s+9-patch\\s+image\\s+(.+)\\s+malformed\\.$"); //$NON-NLS-1$

    private final static Pattern sPattern8Line1 = Pattern.compile(
            "^(invalid resource directory name): (.*)$"); //$NON-NLS-1$

    /**
     * 2 line aapt error<br>
     * "ERROR: Invalid configuration: foo"<br>
     * "                              ^^^"<br>
     * There's no need to parse the 2nd line.
     */
    private final static Pattern sPattern9Line1 = Pattern.compile(
            "^Invalid configuration: (.+)$"); //$NON-NLS-1$


    /**
     * Parse the output of aapt and mark the incorrect file with error markers
     *
     * @param results the output of aapt
     * @param project the project containing the file to mark
     * @return true if the parsing failed, false if success.
     */
    static final boolean parseOutput(ArrayList<String> results,
            IProject project) {
        // nothing to parse? just return false;
        if (results.size() == 0) {
            return false;
        }

        // get the root of the project so that we can make IFile from full
        // file path
        String osRoot = project.getLocation().toOSString();

        Matcher m;

        for (int i = 0; i < results.size(); i++) {
            String p = results.get(i);

            m = sPattern0Line1.matcher(p);
            if (m.matches()) {
                // we ignore those (as this is an ignore message from aapt)
                continue;
            }

            m = sPattern1Line1.matcher(p);
            if (m.matches()) {
                String lineStr = m.group(1);
                String msg = m.group(2);

                // get the matcher for the next line.
                m = getNextLineMatcher(results, ++i, sPattern1Line2);
                if (m == null) {
                    return true;
                }

                String location = m.group(1);

                // check the values and attempt to mark the file.
                if (checkAndMark(location, lineStr, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_COMPILE, IMarker.SEVERITY_ERROR) == false) {
                    return true;
                }
                continue;
            }

            // this needs to be tested before Pattern2 since they both start with 'ERROR:'
            m = sPattern7Line1.matcher(p);
            if (m.matches()) {
                String location = m.group(1);
                String msg = p; // default msg is the line in case we don't find anything else

                if (++i < results.size()) {
                    msg = results.get(i).trim();
                    if (++i < results.size()) {
                        msg = msg + " - " + results.get(i).trim(); //$NON-NLS-1$

                        // skip the next line
                        i++;
                    }
                }

                // display the error
                if (checkAndMark(location, null, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_COMPILE, IMarker.SEVERITY_ERROR) == false) {
                    return true;
                }

                // success, go to the next line
                continue;
            }

            m =  sPattern2Line1.matcher(p);
            if (m.matches()) {
                // get the msg
                String msg = m.group(1);

                // get the matcher for the next line.
                m = getNextLineMatcher(results, ++i, sPattern2Line2);
                if (m == null) {
                    return true;
                }

                String location = m.group(1);
                String lineStr = m.group(2);

                // check the values and attempt to mark the file.
                if (checkAndMark(location, lineStr, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_COMPILE, IMarker.SEVERITY_ERROR) == false) {
                    return true;
                }
                continue;
            }

            m = sPattern3Line1.matcher(p);
            if (m.matches()) {
                String location = m.group(1);
                String lineStr = m.group(2);
                String msg = m.group(3);

                // check the values and attempt to mark the file.
                if (checkAndMark(location, lineStr, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_COMPILE, IMarker.SEVERITY_ERROR) == false) {
                    return true;
                }

                // success, go to the next line
                continue;
            }

            m = sPattern4Line1.matcher(p);
            if (m.matches()) {
                // get the filename.
                String location = m.group(1);

                // get the matcher for the next line.
                m = getNextLineMatcher(results, ++i, sPattern4Line2);
                if (m == null) {
                    return true;
                }

                String msg = m.group(1);
                String lineStr = m.group(2);

                // check the values and attempt to mark the file.
                if (checkAndMark(location, lineStr, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_COMPILE, IMarker.SEVERITY_ERROR) == false) {
                    return true;
                }

                // success, go to the next line
                continue;
            }

            m = sPattern5Line1.matcher(p);
            if (m.matches()) {
                String location = m.group(1);
                String lineStr = m.group(2);
                String msg = m.group(3);

                // check the values and attempt to mark the file.
                if (checkAndMark(location, lineStr, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_COMPILE, IMarker.SEVERITY_WARNING) == false) {
                    return true;
                }

                // success, go to the next line
                continue;
            }

            m = sPattern6Line1.matcher(p);
            if (m.matches()) {
                String location = m.group(1);
                String lineStr = m.group(2);
                String msg = m.group(3);

                // check the values and attempt to mark the file.
                if (checkAndMark(location, lineStr, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_COMPILE, IMarker.SEVERITY_ERROR) == false) {
                    return true;
                }

                // success, go to the next line
                continue;
            }

            m = sPattern8Line1.matcher(p);
            if (m.matches()) {
                String location = m.group(2);
                String msg = m.group(1);

                // check the values and attempt to mark the file.
                if (checkAndMark(location, null, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_COMPILE, IMarker.SEVERITY_ERROR) == false) {
                    return true;
                }

                // success, go to the next line
                continue;
            }

            m = sPattern9Line1.matcher(p);
            if (m.matches()) {
                String badConfig = m.group(1);
                String msg = String.format("APK Configuration filter '%1$s' is invalid", badConfig);

                // skip the next line
                i++;

                // check the values and attempt to mark the file.
                if (checkAndMark(null /*location*/, null, msg, osRoot, project,
                        AndroidConstants.MARKER_AAPT_PACKAGE, IMarker.SEVERITY_ERROR) == false) {
                    return true;
                }

                // success, go to the next line
                continue;
            }

            // invalid line format, flag as error, and bail
            return true;
        }

        return false;
    }

    /**
     * Check if the parameters gotten from the error output are valid, and mark
     * the file with an AAPT marker.
     * @param location the full OS path of the error file. If null, the project is marked
     * @param lineStr
     * @param message
     * @param root The root directory of the project, in OS specific format.
     * @param project
     * @param markerId The marker id to put.
     * @param severity The severity of the marker to put (IMarker.SEVERITY_*)
     * @return true if the parameters were valid and the file was marked successfully.
     *
     * @see IMarker
     */
    private static final  boolean checkAndMark(String location, String lineStr,
            String message, String root, IProject project, String markerId, int severity) {
        // check this is in fact a file
        if (location != null) {
            File f = new File(location);
            if (f.exists() == false) {
                return false;
            }
        }

        // get the line number
        int line = -1; // default value for error with no line.

        if (lineStr != null) {
            try {
                line = Integer.parseInt(lineStr);
            } catch (NumberFormatException e) {
                // looks like the string we extracted wasn't a valid
                // file number. Parsing failed and we return true
                return false;
            }
        }

        // add the marker
        IResource f2 = project;
        if (location != null) {
            f2 = getResourceFromFullPath(location, root, project);
            if (f2 == null) {
                return false;
            }
        }

        // check if there's a similar marker already, since aapt is launched twice
        boolean markerAlreadyExists = false;
        try {
            IMarker[] markers = f2.findMarkers(markerId, true, IResource.DEPTH_ZERO);

            for (IMarker marker : markers) {
                int tmpLine = marker.getAttribute(IMarker.LINE_NUMBER, -1);
                if (tmpLine != line) {
                    break;
                }

                int tmpSeverity = marker.getAttribute(IMarker.SEVERITY, -1);
                if (tmpSeverity != severity) {
                    break;
                }

                String tmpMsg = marker.getAttribute(IMarker.MESSAGE, null);
                if (tmpMsg == null || tmpMsg.equals(message) == false) {
                    break;
                }

                // if we're here, all the marker attributes are equals, we found it
                // and exit
                markerAlreadyExists = true;
                break;
            }

        } catch (CoreException e) {
            // if we couldn't get the markers, then we just mark the file again
            // (since markerAlreadyExists is initialized to false, we do nothing)
        }

        if (markerAlreadyExists == false) {
            BaseProjectHelper.markResource(f2, markerId, message, line, severity);
        }

        return true;
    }

    /**
     * Returns a matching matcher for the next line
     * @param lines The array of lines
     * @param nextIndex The index of the next line
     * @param pattern The pattern to match
     * @return null if error or no match, the matcher otherwise.
     */
    private static final Matcher getNextLineMatcher(ArrayList<String> lines,
            int nextIndex, Pattern pattern) {
        // unless we can't, because we reached the last line
        if (nextIndex == lines.size()) {
            // we expected a 2nd line, so we flag as error
            // and we bail
            return null;
        }

        Matcher m = pattern.matcher(lines.get(nextIndex));
        if (m.matches()) {
           return m;
        }

        return null;
    }

    private static IResource getResourceFromFullPath(String filename, String root,
            IProject project) {
        if (filename.startsWith(root)) {
            String file = filename.substring(root.length());

            // get the resource
            IResource r = project.findMember(file);

            // if the resource is valid, we add the marker
            if (r.exists()) {
                return r;
            }
        }

        return null;
    }

}
