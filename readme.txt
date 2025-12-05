========================================
NETWORK COVERAGE - WINDOWS INSTALLATION
========================================

QUICK START:
1. Double-click 0-quick-start.bat
2. Choose option 4 (Build + Create EXE)
3. Wait for the process to complete
4. Find installer in dist\ folder

DETAILED STEPS:

STEP 1: BUILD THE APPLICATION
------------------------------
Option A: Using batch file
   - Double-click 1-build.bat
   - Wait for "BUILD SUCCESSFUL" message
   - Creates NetworkCoverage.jar

Option B: Using Maven directly
   - Open Command Prompt in this folder
   - Run: mvn clean package
   - Copy: copy target\network-coverage-1.0.0.jar NetworkCoverage.jar

STEP 2: RUN THE APPLICATION
---------------------------
Option A: If Java is installed
   - Double-click 2-run.bat
   OR
   - Double-click NetworkCoverage.jar
   OR
   - Run: java -jar NetworkCoverage.jar

Option B: Create EXE installer (no Java needed)
   - Double-click 3-create-exe.bat
   - Installer: dist\Network Coverage-1.0.0.exe
   - Users install like any Windows software

REQUIREMENTS FOR BUILDING:
1. JDK 17 or later (for jpackage)
   Download: https://adoptium.net/
2. Maven 3.6+ 
   Download: https://maven.apache.org/
3. Wix Toolset (for EXE creation - optional)
   Download: https://wixtoolset.org/

REQUIREMENTS FOR RUNNING (JAR):
1. Java 17 or later (JRE sufficient)
   Download: https://www.java.com/

REQUIREMENTS FOR RUNNING (EXE):
1. Windows 10 or 11
2. No Java required!

TROUBLESHOOTING:

Problem: "Java not found"
Solution: Install Java 17+ and add to PATH

Problem: "jpackage not found"
Solution: Install JDK 17+ (not just JRE)

Problem: Wix Toolset error
Solution: Install Wix Toolset or skip EXE creation

Problem: Application won't start
Solution: 
  1. Check Java version: java -version
  2. Rebuild: 1-build.bat
  3. Run from Command Prompt to see errors

DEFAULT LOGIN CREDENTIALS:
- Admin:     admin / admin123
- Operator:  operator / operator123
- Viewer:    viewer / viewer123

API ACCESS:
- REST API: http://localhost:4567
- Requires authentication
- Access API docs from application

SUPPORT:
1. Check console for error messages
2. Ensure port 4567 is not blocked by firewall
3. Run 0-quick-start.bat for guided setup