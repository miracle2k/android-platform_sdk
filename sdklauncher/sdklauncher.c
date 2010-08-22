/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * The "SDK Manager" is for Windows only.
 * This simple .exe will sit at the root of the Windows SDK
 * and currently simply executes tools\android.bat.
 * Eventually it should simply replace the batch file.
 *
 * TODO:
 * - create temp dir, always copy *.jar there, exec android.jar
 * - get jars to copy from some file
 * - use a version number to copy jars only if needed (tools.revision?)
 */

#ifdef _WIN32

#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <windows.h>


int _enable_dprintf = 0;

void dprintf(char *msg, ...) {
    va_list ap;
    va_start(ap, msg);

    if (_enable_dprintf) {
        vfprintf(stderr, msg, ap);
    }

    va_end(ap);
}

void display_error(LPSTR description) {
    DWORD err = GetLastError();
    LPSTR s, s2;

    fprintf(stderr, "%s, error %ld\n", description, err);

    if (FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER | /* dwFlags */
                      FORMAT_MESSAGE_FROM_SYSTEM,
                      NULL,                             /* lpSource */
                      err,                              /* dwMessageId */
                      0,                                /* dwLanguageId */
                      (LPSTR)&s,                        /* lpBuffer */
                      0,                                /* nSize */
                      NULL) != 0) {                     /* va_list args */
        fprintf(stderr, "%s", s);

        s2 = (LPSTR) malloc(strlen(description) + strlen(s) + 5);
        sprintf(s2, "%s\r\n%s", description, s);
        MessageBox(NULL, s2, "Android SDK Manager - Error", MB_OK);
        free(s2);
        LocalFree(s);
    }
}


HANDLE create_temp_file(LPSTR temp_filename) {

    HANDLE file_handle = INVALID_HANDLE_VALUE;
    LPSTR temp_path = (LPSTR) malloc(MAX_PATH);

    /* Get the temp directory path using GetTempPath.
       GetTempFilename indicates that the temp path dir should not be larger than MAX_PATH-14.
    */
    int ret = GetTempPath(MAX_PATH - 14, temp_path);
    if (ret > MAX_PATH || ret == 0) {
        display_error("GetTempPath failed");
        free(temp_path);
        return INVALID_HANDLE_VALUE;
    }

    /* Now get a temp filename in the temp directory. */
    if (!GetTempFileName(temp_path, "txt", 0, temp_filename)) {
        display_error("GetTempFileName failed");

    } else {
        SECURITY_ATTRIBUTES sattr;
        ZeroMemory(&sattr, sizeof(sattr));
        sattr.nLength = sizeof(SECURITY_ATTRIBUTES);
        sattr.bInheritHandle = TRUE;

        file_handle = CreateFile(temp_filename,             // filename
                                 GENERIC_WRITE,             // access: write
                                 FILE_SHARE_READ,           // share mode: read OK
                                 &sattr,                    // security attributes
                                 CREATE_ALWAYS,             // create even if exists
                                 FILE_ATTRIBUTE_NORMAL,     // flags and attributes
                                 NULL);                     // template
        if (file_handle == INVALID_HANDLE_VALUE) {
            display_error("Create temp file failed");
        }
    }

    free(temp_path);
    return file_handle;
}


void read_temp_file(LPSTR temp_filename) {
    HANDLE handle;

    handle = CreateFile(temp_filename,             // filename
                        GENERIC_READ,              // access: read
                        FILE_SHARE_READ,           // share mode: read OK
                        NULL,                      // security attributes
                        OPEN_EXISTING,             // only open existing file
                        FILE_ATTRIBUTE_NORMAL,     // flags and attributes
                        NULL);                     // template

    if (handle == INVALID_HANDLE_VALUE) {
        display_error("Open temp file failed");
        return;
    }

    /* Cap the size we're reading.
       4K is good enough to display in a message box.
    */
    DWORD size = 4096;

    LPSTR buffer = (LPSTR) malloc(size + 1);

    LPSTR p = buffer;
    DWORD num_left = size;
    DWORD num_read;
    do {
        if (!ReadFile(handle, p, num_left, &num_read, NULL)) {
            display_error("Read Output failed");
            break;
        }

        num_left -= num_read;
        p += num_read;
    } while (num_read > 0);

    if (p != buffer) {
        *p = 0;

        /* Only output the buffer if it contains special keywords WARNING or ERROR. */
        char* s1 = strstr(buffer, "WARNING");
        char* s2 = strstr(buffer, "ERROR");

        if (s2 != NULL && s2 < s1) {
            s1 = s2;
        }

        if (s1 != NULL) {
            /* We end the message at the first occurence of [INFO]. */
            s2 = strstr(s1, "[INFO]");
            if (s2 != NULL) {
                *s2 = 0;
            }

            MessageBox(NULL, s1, "Android SDK Manager - Output", MB_OK);
        }

    }

    free(buffer);

    if (!CloseHandle(handle)) {
        display_error("CloseHandle read temp file failed");
    }
}


int sdk_launcher() {
    int                   result = 0;
    STARTUPINFO           startup;
    PROCESS_INFORMATION   pinfo;
    CHAR                  program_dir[MAX_PATH];
    int                   ret, pos;
    CHAR                  temp_filename[MAX_PATH];
    HANDLE                temp_handle;

    ZeroMemory(&pinfo, sizeof(pinfo));

    temp_handle = create_temp_file(temp_filename);
    if (temp_handle == INVALID_HANDLE_VALUE) {
        return 1;
    }

    ZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    startup.dwFlags    = STARTF_USESTDHANDLES;
    startup.hStdInput  = GetStdHandle(STD_INPUT_HANDLE);
    startup.hStdOutput = temp_handle;
    startup.hStdError  = temp_handle;

    /* get path of current program, to switch dirs here when executing the command. */
    ret = GetModuleFileName(NULL, program_dir, sizeof(program_dir));
    if (ret == 0) {
        display_error("Failed to get program's filename:");
        result = 1;
    } else {
        /* Remove the last segment to keep only the directory. */
        pos = ret - 1;
        while (pos > 0 && program_dir[pos] != '\\') {
            --pos;
        }
        program_dir[pos] = 0;
    }

    if (!result) {
        dprintf("Program dir: %s\n", program_dir);

        ret = CreateProcess(
                NULL,                                       /* program path */
                "tools\\android.bat update sdk",           /* command-line */
                NULL,                  /* process handle is not inheritable */
                NULL,                   /* thread handle is not inheritable */
                TRUE,                          /* yes, inherit some handles */
                CREATE_NO_WINDOW,                /* we don't want a console */
                NULL,                     /* use parent's environment block */
                program_dir,             /* use parent's starting directory */
                &startup,                 /* startup info, i.e. std handles */
                &pinfo);
               
        dprintf("CreateProcess returned %d\n", ret);

        if (!ret) {
            display_error("Failed to execute tools\\android.bat:");
            result = 1;
        } else {
            dprintf("Wait for process to finish.\n");
            
            WaitForSingleObject(pinfo.hProcess, INFINITE);
            CloseHandle(pinfo.hProcess);
            CloseHandle(pinfo.hThread);
        }
    }
    
    dprintf("Cleanup.\n");

    if (!CloseHandle(temp_handle)) {
        display_error("CloseHandle temp file failed");
    }

    if (!result) {
        read_temp_file(temp_filename);
    }

    if (!DeleteFile(temp_filename)) {
        display_error("Delete temp file failed");
    }

    return result;
}

int main(int argc, char **argv) {
    _enable_dprintf = argc > 1 && strcmp(argv[1], "-v") == 0;
    dprintf("Verbose debug mode.\n");
    
    return sdk_launcher();
}

#endif /* _WIN32 */
