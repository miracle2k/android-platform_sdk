@echo off
rem Copyright (C) 2007 The Android Open Source Project
rem
rem Licensed under the Apache License, Version 2.0 (the "License");
rem you may not use this file except in compliance with the License.
rem You may obtain a copy of the License at
rem
rem      http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

rem This script is called by the other batch files to find a suitable Java.exe
rem to use. The script changes the "java_exe" env variable. The variable
rem is left unset if Java.exe was not found.

rem Useful links:
rem Command-line reference:
rem   http://technet.microsoft.com/en-us/library/bb490890.aspx

rem Check we have a valid Java.exe in the path. The return code will
rem be 0 if the command worked or 9009 if the exec failed (program not found).
rem Java itself will return 1 if the argument is not understood.
set java_exe=java
%java_exe% -version 2>nul
if ERRORLEVEL 1 goto SearchForJava
goto :EOF


rem ---------------
:SearchForJava
rem We get here if the default %java_exe% was not found in the path.
rem Search for an alternative in %ProgramFiles%\Java\*\bin\java.exe

echo.
echo WARNING: Java not found in your path.

rem Check if there's a 64-bit version of Java in %ProgramW6432%
if not defined ProgramW6432 goto :Check32
echo Checking if it's installed in %ProgramW6432%\Java instead (64-bit).

set java_exe=
for /D %%a in ( "%ProgramW6432%\Java\*" ) do call :TestJavaDir "%%a"
if defined java_exe goto :EOF

rem Check for the "default" 32-bit version
:Check32
echo Checking if it's installed in %ProgramFiles%\Java instead.

set java_exe=
for /D %%a in ( "%ProgramFiles%\Java\*" ) do call :TestJavaDir "%%a"
if defined java_exe goto :EOF

echo.
echo ERROR: No suitable Java found. In order to properly use the Android Developer
echo Tools, you need a suitable version of Java JDK installed on your system.
echo We recommend that you install the JDK version of JavaSE, available here:
echo   http://www.oracle.com/technetwork/java/javase/downloads
echo.
echo You can find the complete Android SDK requirements here:
echo   http://developer.android.com/sdk/requirements.html
echo.
goto :EOF

rem ---------------
:TestJavaDir
rem This is a "subrountine" for the for /D above. It tests the short version
rem of the %1 path (i.e. the path with only short names and no spaces).
rem However we use the full version without quotes (e.g. %~1) for pretty print.
if defined java_exe goto :EOF
set full_path=%~1\bin\java.exe
set short_path=%~s1\bin\java.exe

%short_path% -version 2>nul
if ERRORLEVEL 1 goto :EOF
set java_exe=%short_path%

echo.
echo Java was found at %full_path%.
echo Please consider adding it to your path:
echo - Under Windows XP, open Control Panel / System / Advanced / Environment Variables
echo - Under Windows Vista or Windows 7, open Control Panel / System / Advanced System Settings / Environment Variables
echo At the end of the "Path" entry in "User variables", add the following:
echo   ;%full_path%
echo.
