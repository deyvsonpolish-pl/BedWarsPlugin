@echo off
title BedWars Auto Release

call mvn clean package -U

git add .
git commit -m "auto release"
git push -f

set /p version=Podaj wersje (np. v1.0.0):

git tag -d %version% 2>nul
git push origin :refs/tags/%version% 2>nul

git tag %version%
git push origin %version%

echo DONE 🔥
pause