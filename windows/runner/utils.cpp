#include "utils.h"

#include <flutter_windows.h>
#include <io.h>
#include <stdio.h>
#include <windows.h>

#include <iostream>

void CreateAndAttachConsole() {
  if (::AllocConsole()) {
    FILE *unused;
    if (freopen_s(&unused, "CONOUT$", "w", stdout)) {
      _dup2(_fileno(stdout), 1);
    }
    if (freopen_s(&unused, "CONOUT$", "w", stderr)) {
      _dup2(_fileno(stdout), 2);
    }
    std::ios::sync_with_stdio();
    FlutterDesktopResyncOutputStreams();
  }
}

std::vector<std::string> GetCommandLineArguments() {
  // Convert the UTF-16 command line arguments to UTF-8 for the Engine to use.
  int argc;
  wchar_t** argv = ::CommandLineToArgvW(::GetCommandLineW(), &argc);
  if (argv == nullptr) {
    return std::vector<std::string>();
  }

  std::vector<std::string> command_line_arguments;

  // Skip the first argument as it's the binary name.
  for (int i = 1; i < argc; i++) {
    command_line_arguments.push_back(Utf8FromUtf16(argv[i]));
  }

  ::LocalFree(argv);

  return command_line_arguments;
}

std::string Utf8FromUtf16(const wchar_t* utf16_string) {
  if (utf16_string == nullptr) {
    return std::string();
  }
  int target_length = ::WideCharToMultiByte(
      CP_UTF8, WC_ERR_INVALID_CHARS, utf16_string,
      -1, nullptr, 0, nullptr, nullptr)
    -1; // remove the trailing null character
  int input_length = (int)wcslen(utf16_string);
  std::string utf8_string;
  if (target_length <= 0 || target_length > utf8_string.max_size()) {
    return utf8_string;
  }
  utf8_string.resize(target_length);
  int converted_length = ::WideCharToMultiByte(
      CP_UTF8, WC_ERR_INVALID_CHARS, utf16_string,
      input_length, utf8_string.data(), target_length, nullptr, nullptr);
  if (converted_length == 0) {
    return std::string();
  }
  return utf8_string;
}

std::wstring Utf8ToUtf16(const char* utf8_string) {
    // ref: filled by chatGPT 4 turbo
    if (utf8_string == nullptr) {
        return std::wstring(); // Return an empty wstring if the input is null.
    }

    // Calculate the length of the resulting wide string.
    int wide_char_length = MultiByteToWideChar(
            CP_UTF8,            // Source string is in UTF-8
            0,                  // No flags
            utf8_string,        // Source UTF-8 string
            -1,                 // The string is null-terminated
            nullptr,            // No output buffer since we're calculating the length
            0                   // Request length calculation
    );

    if (wide_char_length == 0) {
        // Handle the error, could be due to an invalid UTF-8 sequence.
        // GetLastError() can be used to get more information.
        return std::wstring();
    }

    // Allocate a buffer for the wide string.
    std::wstring utf16_string(wide_char_length, L'\0');

    // Now convert the UTF-8 string to UTF-16.
    int convert_result = MultiByteToWideChar(
            CP_UTF8,            // Source string is in UTF-8
            0,                  // No flags
            utf8_string,        // Source UTF-8 string
            -1,                 // The string is null-terminated
            &utf16_string[0],   // Output buffer for the wide string
            wide_char_length    // Size of the output buffer
    );

    if (convert_result == 0) {
        // Handle the error, could be due to an invalid UTF-8 sequence.
        // GetLastError() can be used to get more information.
        return std::wstring();
    }

    // The length includes the null terminator, so we resize to remove it.
    utf16_string.resize(wide_char_length - 1);

    return utf16_string;
}