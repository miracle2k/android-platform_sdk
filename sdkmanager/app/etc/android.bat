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

rem Useful links:
rem Command-line reference:
rem   http://technet.microsoft.com/en-us/library/bb490890.aspx

rem don't modify the caller's environment
setlocal

rem Set up prog to be the path of this script, including following symlinks,
rem and set up progdir to be the fully-qualified pathname of its directory.
set prog=%~f0

rem Grab current directory before we change it
set work_dir=%cd%

rem Change current directory and drive to where the script is, to avoid
rem issues with directories containing whitespaces.
cd /d %~dp0


rem Check we have a valid Java.exe in the path. The return code will
rem be 0 if the command worked or 9009 if the exec failed (program not found).
rem Java itself will return 1 if the argument is not understood.
set java_exe=java
%java_exe% -version 2>nul
if ERRORLEVEL 1 goto SearchForJava
:JavaFound

set jar_path=lib\sdkmanager.jar

rem Set SWT.Jar path based on current architecture (x86 or x86_64)
for /f %%a in ('%java_exe% -jar lib\archquery.jar') do set swt_path=lib\%%a


if "%1 %2"=="update sdk" goto StartUi
if not "%1"=="" goto EndTempCopy
:StartUi
    echo Starting Android SDK and AVD Manager

    rem We're now going to create a temp dir to hold all the Jar files needed
    rem to run the android tool, copy them in the temp dir and finally execute
    rem from that path. We do this only when the launcher is run without
    rem arguments, to display the SDK Updater UI. This allows the updater to
    rem update the tools directory where the updater itself is located.

    set tmp_dir=%TEMP%\temp-android-tool
    xcopy lib\x86 %tmp_dir%\lib\x86 /I /E /C /G /R /Y /Q > nul
    copy /B /D /Y lib\androidprefs.jar   %tmp_dir%\lib\       > nul
    copy /B /D /Y lib\org.eclipse.*      %tmp_dir%\lib\       > nul
    copy /B /D /Y lib\sdk*               %tmp_dir%\lib\       > nul
    copy /B /D /Y lib\commons-compress*  %tmp_dir%\lib\       > nul

    rem jar_path and swt_path are relative to PWD so we don't need to adjust them, just change dirs.
    set tools_dir=%cd%
    cd %tmp_dir%

:EndTempCopy
    
rem The global ANDROID_SWT always override the SWT.Jar path
if defined ANDROID_SWT set swt_path=%ANDROID_SWT%

if exist %swt_path% goto SetPath
    echo SWT folder '%swt_path%' does not exist.
    echo Please set ANDROID_SWT to point to the folder containing swt.jar for your platform.
    exit /B

:SetPath
set java_ext_dirs=%swt_path%;lib\

rem Finally exec the java program and end here.
call %java_exe% -Djava.ext.dirs=%java_ext_dirs% -Dcom.android.sdkmanager.toolsdir="%tools_dir%" -Dcom.android.sdkmanager.workdir="%work_dir%" -jar %jar_path% %*
goto :EOF

rem ---------------
:SearchForJava
rem We get here if the default %java_exe% was not found in the path.
rem Search for an alternative in %ProgramFiles%\Java\*\bin\java.exe

echo.
echo Java not found in your path.
echo Checking it it's installed in %ProgramFiles%\Java instead.
echo.

set java_exe=
for /D %%a in ( "%ProgramFiles%\Java\*" ) do call :TestJavaDir "%%a"
if defined java_exe goto JavaFound

echo.
echo No suitable Java found. In order to properly use the Android Developer Tools,
echo you need a suitable version of Java installed on your system. We recommend
echo that you install the JDK version of JavaSE, available here:
echo   http://java.sun.com/javase/downloads/
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
rem [for debugging] echo Testing %full_path%

%short_path% -version 2>nul
if ERRORLEVEL 1 goto :EOF
set java_exe=%short_path%

echo.
echo Java was found at %full_path%.
echo Please consider adding it to your path:
echo - Under Windows XP, open Control Panel / System / Advanced / Environment Variables
echo - Under Windows Vista, open Control Panel / System / Advanced System Settings
echo                                                    / Environment Variables
echo At the end of the "Path" entry in "User variables", add the following:
echo   ;%full_path%
echo.

rem EOF
