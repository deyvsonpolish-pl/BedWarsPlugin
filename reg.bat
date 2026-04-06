@echo off
REM release.bat v1.95
set VERSION=%1
gh release create %VERSION% "C:\Users\dawid\Desktop\IdeaProjects\BedWarsPlugin\target\BedWarsPlugin-0.0.0.jar" --title "BedWarsPlugin %VERSION%" --notes "Dodano nowe funkcje i poprawki"
pause
