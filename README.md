# RestoreCRC32
 Android app to set the CRC32 and date modified of files in an APK or ZIP archive to those from another archive.
 
 Some apps on Android (example: apps with Google Pairip Integrity protection) verify the validity of the app by checking the CRC32 and last modified date of files in its own APK. Restoring the values from the original file to a modified one can help in reverse analysis of the application. 
 
 Note: This is not the only step required in bypassing Pairip and it doesn't work on the latest version of Pairip
# Usage
There are 2 ways to use:
 * Open the app, use the buttons to select the original file and file to patch
 * Select multiple files and Share them, then select Restore CRC32 in the available options. Then you will see a dialog to pick the original file. After choosing, another dialog wiill allow you to choose which file to patch.
   * You can also patch multiple files at once in this way (May be useful if someone forgot to patch the CRC32 before splitting architectures or something).
 
 There are options to only patch the CRC32 of AndroidManifest.xml and classes.dex files, and to toggle the restoration of the last modified date of files in the archive.
# Screenshots
Screen after sharing files with the app

<img src=".github/readme-images/Screenshot_2024-05-09-22-46-57-254_com.AbdurazaaqMohammed.restoreCRC.jpg" width="33%" />

Main screen

<img src=".github/readme-images/Screenshot_2024-05-09-22-46-23-282_com.AbdurazaaqMohammed.restoreCRC.jpg" width="33%" />
